package com.irummate.global.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "notification.push",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseConfig {
    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(
            @Value("${firebase.credentials-path}") String credentialsPath
    ) throws IOException {

        if (credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "푸시를 사용하려면 Firebase 인증 파일 경로가 필요합니다."
            );
        }

        try (InputStream stream = Files.newInputStream(Path.of(credentialsPath))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(stream))
                    .build();

            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
