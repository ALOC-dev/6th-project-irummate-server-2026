package com.irummate.domain.notification.scheduler;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.TopicManagementResponse;
import com.irummate.domain.notification.entity.PushJob.Payload;
import com.irummate.domain.notification.entity.PushJobStatus;
import com.irummate.domain.notification.repository.PushDeviceRepository;
import com.irummate.domain.notification.repository.PushJobRepository;
import com.irummate.domain.notification.repository.PushJobRepository.ClaimedJob;
import com.irummate.domain.notification.repository.TopicSubscriptionRepository;
import com.irummate.domain.notification.repository.TopicSubscriptionRepository.Change;
import com.irummate.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true")
public class PushJobScheduler {
    private static final int BATCH_SIZE = 50;
    private static final int SUBSCRIPTION_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;
    private static final Set<String> INVALID_TOPIC_TOKENS = Set.of(
            "invalid-registration-token", "registration-token-not-registered", "not-found", "unregistered");

    private final PushJobRepository jobs;
    private final PushDeviceRepository devices;
    private final TopicSubscriptionRepository subscriptions;
    private final FirebaseMessaging firebase;
    private final ObjectMapper json;

    @Scheduled(fixedDelayString = "${notification.push.poll-delay-ms:2000}")
    public void process() {
        runSafely("토픽 구독", this::syncSubscriptions);
        runSafely("푸시 발송", this::sendJobs);
    }

    private void syncSubscriptions(UUID lease) {
        var byDesiredState = subscriptions.claim(lease, SUBSCRIPTION_BATCH_SIZE).stream()
                .collect(Collectors.partitioningBy(Change::subscribe));
        syncTopic(byDesiredState.get(false), false, lease);
        syncTopic(byDesiredState.get(true), true, lease);
    }

    private void syncTopic(List<Change> changes, boolean subscribe, UUID lease) {
        if (changes.isEmpty()) {
            return;
        }
        var tokens = changes.stream().map(Change::token).toList();
        TopicManagementResponse result;
        try {
            result = subscribe
                    ? firebase.subscribeToTopic(tokens, NotificationService.ANNOUNCEMENT_TOPIC)
                    : firebase.unsubscribeFromTopic(tokens, NotificationService.ANNOUNCEMENT_TOPIC);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 토픽 구독 변경 실패: code={}", errorCode(e));
            tokens.forEach(token -> subscriptions.finish(token, lease, null));
            return;
        }
        var errors = result.getErrors().stream().collect(Collectors.toMap(
                TopicManagementResponse.Error::getIndex, TopicManagementResponse.Error::getReason));
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String reason = errors.get(i);
            if (reason == null) {
                subscriptions.finish(token, lease, subscribe);
            } else if (INVALID_TOPIC_TOKENS.contains(reason.toLowerCase(Locale.ROOT).replace('_', '-'))) {
                devices.invalidate(token);
                subscriptions.finish(token, lease, false);
            } else {
                subscriptions.finish(token, lease, null);
            }
        }
    }

    private void sendJobs(UUID lease) {
        var claimed = jobs.claim(lease, BATCH_SIZE);
        var deviceJobs = new ArrayList<ClaimedJob>();
        var topicJobs = new ArrayList<ClaimedJob>();
        for (var job : claimed) {
            if (job.expired()) {
                jobs.finish(job.id(), lease, PushJobStatus.SKIPPED, "EXPIRED", 0);
            } else if (job.attempts() > MAX_ATTEMPTS) {
                jobs.finish(job.id(), lease, PushJobStatus.FAILED, "ATTEMPTS_EXHAUSTED", 0);
            } else if (job.topic() == null) {
                deviceJobs.add(job);
            } else {
                topicJobs.add(job);
            }
        }
        sendDevices(deviceJobs, lease);
        topicJobs.forEach(job -> sendTopic(job, lease));
    }

    private void sendTopic(ClaimedJob job, UUID lease) {
        // 구독 변경을 Firebase에 반영한 뒤 전체 공지를 보낸다.
        if (subscriptions.hasOutstandingChanges()) {
            jobs.deferTopic(job.id(), lease);
            return;
        }
        try {
            var payload = parse(job);
            firebase.send(Message.builder().setTopic(job.topic()).setNotification(notification(payload))
                    .putAllData(payload.data()).build());
            jobs.finish(job.id(), lease, PushJobStatus.SENT, null, 0);
        } catch (JacksonException e) {
            jobs.finish(job.id(), lease, PushJobStatus.FAILED, "INVALID_PAYLOAD", 0);
        } catch (FirebaseMessagingException e) {
            recordFailure(job, null, lease, e);
        }
    }

    private void sendDevices(List<ClaimedJob> batch, UUID lease) {
        if (batch.isEmpty()) {
            return;
        }
        var tokens = jobs.eligibleDevices(batch.stream().map(ClaimedJob::id).toList(), lease);
        var eligible = new ArrayList<DeviceDelivery>();
        for (var job : batch) {
            String token = tokens.get(job.id());
            if (token == null) {
                jobs.finish(job.id(), lease, PushJobStatus.SKIPPED, "DEVICE_NOT_ELIGIBLE", 0);
                continue;
            }
            try {
                eligible.add(new DeviceDelivery(job, token, parse(job)));
            } catch (JacksonException e) {
                jobs.finish(job.id(), lease, PushJobStatus.FAILED, "INVALID_PAYLOAD", 0);
            }
        }
        if (eligible.isEmpty()) {
            return;
        }

        BatchResponse result;
        try {
            result = sendDeviceBatch(eligible);
        } catch (FirebaseMessagingException e) {
            eligible.forEach(delivery -> recordFailure(delivery.job(), delivery.token(), lease, e));
            return;
        }
        // FCM 응답은 요청 기기 순서대로 돌아온다.
        for (int i = 0; i < eligible.size(); i++) {
            var delivery = eligible.get(i);
            var response = result.getResponses().get(i);
            if (response.isSuccessful()) {
                jobs.finish(delivery.job().id(), lease, PushJobStatus.SENT, null, 0);
            } else {
                recordFailure(delivery.job(), delivery.token(), lease, response.getException());
            }
        }
    }

    private BatchResponse sendDeviceBatch(List<DeviceDelivery> deliveries) throws FirebaseMessagingException {
        var first = deliveries.getFirst().payload();
        if (deliveries.size() > 1 && deliveries.stream().allMatch(item -> first.equals(item.payload()))) {
            return firebase.sendEachForMulticast(MulticastMessage.builder().setNotification(notification(first))
                    .putAllData(first.data()).addAllTokens(deliveries.stream().map(DeviceDelivery::token).toList()).build());
        }
        return firebase.sendEach(deliveries.stream().map(item -> Message.builder().setToken(item.token())
                .setNotification(notification(item.payload())).putAllData(item.payload().data()).build()).toList());
    }

    private void recordFailure(ClaimedJob job, String token, UUID lease, FirebaseMessagingException error) {
        MessagingErrorCode code = error.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED && token != null) {
            devices.invalidate(token);
        }
        if (job.attempts() >= MAX_ATTEMPTS || code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT || code == MessagingErrorCode.SENDER_ID_MISMATCH
                || code == MessagingErrorCode.THIRD_PARTY_AUTH_ERROR) {
            jobs.finish(job.id(), lease, PushJobStatus.FAILED, errorCode(error), 0);
            return;
        }
        int backoff = 10 * (1 << (job.attempts() - 1));
        if (code == MessagingErrorCode.QUOTA_EXCEEDED) {
            backoff = Math.max(backoff, 60);
        }
        jobs.finish(job.id(), lease, PushJobStatus.PENDING, errorCode(error), backoff);
    }

    private Payload parse(ClaimedJob job) {
        return json.readValue(job.payload(), Payload.class);
    }

    private static Notification notification(Payload payload) {
        return Notification.builder().setTitle(payload.title()).setBody(payload.body()).build();
    }

    private static String errorCode(FirebaseMessagingException error) {
        return error.getMessagingErrorCode() == null ? error.getErrorCode().name() : error.getMessagingErrorCode().name();
    }

    private void runSafely(String task, Consumer<UUID> operation) {
        try {
            operation.accept(UUID.randomUUID());
        } catch (RuntimeException e) {
            log.error("{} 작업 실패: type={}", task, e.getClass().getSimpleName());
        }
    }

    private record DeviceDelivery(ClaimedJob job, String token, Payload payload) {}
}
