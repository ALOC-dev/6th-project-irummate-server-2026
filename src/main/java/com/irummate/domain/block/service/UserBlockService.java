package com.irummate.domain.block.service;

import com.irummate.domain.block.dto.BlockedUserResponseDto;
import com.irummate.domain.block.dto.UserBlocksResponseDto;
import com.irummate.domain.block.repository.UserBlockRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

// 차단 API 처리와 다른 도메인에서 재사용할 검증을 담당한다. ID는 실제 회원 PK를 받는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {
    private final UserBlockRepository userBlockRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;

    @Transactional
    // 차단 버튼 요청: 본인 → 상대방 관계를 저장하며 중복 요청은 무시한다.
    public void block(Long blockerId, Long blockedId) {
        validateIds(blockerId, blockedId);
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        requireUser(blockerId);
        requireUser(blockedId);
        userBlockRepository.insertIfAbsent(blockerId, blockedId, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional
    // 차단 해제 요청: 상대방이 나를 차단한 기록에는 영향을 주지 않는다.
    public void unblock(Long blockerId, Long blockedId) {
        validateIds(blockerId, blockedId);
        // 인증된 본인의 방향만 삭제한다. 관계가 없으면 성공으로 처리한다.
        userBlockRepository.deleteBlock(blockerId, blockedId);
    }

    // 내 차단 목록 화면에 사용할 최소 사용자 정보와 페이지 정보를 반환한다.
    public UserBlocksResponseDto findMyBlocks(Long blockerId, int page, int size) {
        validateId(blockerId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var result = userBlockRepository.findBlockedUsers(blockerId, PageRequest.of(page, size));
        // 외부 응답에는 실제 회원 PK 대신 Hashids 식별자를 사용한다.
        var content = result.getContent().stream()
                .map(row -> new BlockedUserResponseDto(hashIdsUtils.encode(row.getUserId()),
                        row.getNickname(), row.getCreatedAt()))
                .toList();
        return new UserBlocksResponseDto(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // 상태 조회 API용: 내가 상대방을 차단했는지만 확인한다.
    public boolean getMyBlockStatus(Long blockerId, Long blockedId) {
        validateIds(blockerId, blockedId);
        requireUser(blockedId);
        return isBlockedBy(blockerId, blockedId);
    }

    // 공통 단방향 검사: blockerId 사용자가 blockedId 사용자를 차단했는지 확인한다.
    public boolean isBlockedBy(Long blockerId, Long blockedId) {
        validateIds(blockerId, blockedId);
        return userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId);
    }

    // 공통 양방향 검사: 어느 한쪽이라도 차단했으면 true이다. 기존 기능에는 아직 미연동이다.
    public boolean isBlockedBetween(Long userA, Long userB) {
        validateIds(userA, userB);
        return userBlockRepository.existsBetween(userA, userB);
    }

    // 향후 관심 요청·메시지 전송 전에 사용할 검증이다. 현재 기존 기능에서는 호출하지 않는다.
    public void validateNotBlocked(Long userA, Long userB) {
        if (isBlockedBetween(userA, userB)) {
            // 어느 방향으로 차단했는지 예외 메시지에 노출하지 않는다.
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
    }

    // 차단 대상 등 회원의 존재 여부 확인이 필요한 API에서 사용한다.
    private void requireUser(Long userId) {
        if (!usersRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    // 두 ID의 형식을 함께 검사한다. 회원 존재 여부는 별도로 확인한다.
    private void validateIds(Long first, Long second) {
        validateId(first);
        validateId(second);
    }

    // null 또는 잘못된 PK가 Repository로 전달되지 않도록 검사한다.
    private void validateId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
