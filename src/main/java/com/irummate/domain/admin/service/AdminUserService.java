package com.irummate.domain.admin.service;

import com.irummate.domain.admin.dto.AdminUserDetailResponseDto;
import com.irummate.domain.admin.dto.AdminUserResponseDto;
import com.irummate.domain.admin.dto.AdminUsersResponseDto;
import com.irummate.domain.admin.dto.AdminUserBanRequestDto;
import com.irummate.domain.admin.entity.UserBan;
import com.irummate.domain.admin.repository.UserBanRepository;
import com.irummate.domain.auth.repository.LoginSessionRepository;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.matching.service.MatchingService;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UsersRepository usersRepository;
    private final CertificationRepository certificationRepository;
    private final HashIdsUtils hashIdsUtils;
    private final MatchingService matchingService;
    private final LoginSessionRepository loginSessionRepository;
    private final UserBanRepository userBanRepository;

    @Transactional(readOnly = true)
    public AdminUsersResponseDto getUsers(int page, int size) {
        validatePageRequest(page, size);

        Page<Users> usersPage = usersRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<AdminUserResponseDto> users = usersPage.getContent()
                .stream()
                .map(user -> AdminUserResponseDto.from(
                        user,
                        hashIdsUtils.encode(user.getId())
                ))
                .toList();

        return new AdminUsersResponseDto(
                users,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponseDto getUser(String userId) {
        Long decodedUserId = decodeUserId(userId);
        Users user = getUser(decodedUserId);
        // 관리자 사용자 상세에도 현재 학기의 인증 상태만 반영
        Certification currentCertification = certificationRepository
                .findByUser_IdAndSemester(decodedUserId, SemesterUtils.currentSemester())
                .orElse(null);

        return AdminUserDetailResponseDto.from(user, userId, currentCertificationStatus(currentCertification));
    }

    @Transactional
    public AdminUserResponseDto banUser(Long adminUserId, String userId, AdminUserBanRequestDto request) {
        Users user = getUser(decodeUserId(userId));
        Users admin = getUser(adminUserId);
        if (user.getId().equals(adminUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "관리자는 자기 자신을 정지할 수 없습니다.");
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴한 계정은 정지할 수 없습니다.");
        }

        user.ban();
        userBanRepository.save(new UserBan(user, admin, request.getReason(), request.getExpiresAt()));
        loginSessionRepository.revokeAllByUserId(user.getId(), LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        // 정지된 계정과 연관된 모든 match request를 CLOSED 처리합니다. (탈퇴 처리와 대칭)
        matchingService.closeAllMatchRequestsByUserId(user.getId());

        return AdminUserResponseDto.from(user, userId);
    }

    @Transactional
    public AdminUserResponseDto unbanUser(Long adminUserId, String userId) {
        Users user = getUser(decodeUserId(userId));
        Users admin = getUser(adminUserId);
        if (user.getStatus() != UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "정지된 계정이 아닙니다.");
        }

        user.unban();
        userBanRepository.findTopByUser_IdAndLiftedAtIsNullOrderByBannedAtDesc(user.getId())
                .ifPresent(ban -> ban.lift(admin));

        return AdminUserResponseDto.from(user, userId);
    }

    // 조회 시 DB 상태를 변경하지 않고 인증 만료 여부를 반영
    private String currentCertificationStatus(Certification certification) {
        if (certification == null) {
            return "NONE";
        }
        if (certification.getCertificationStatus() == CertificationStatus.APPROVED
                && certification.getExpiresAt() != null
                && !certification.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")))) {
            return CertificationStatus.EXPIRED.name();
        }
        return certification.getCertificationStatus().name();
    }

    private Users getUser(Long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Long decodeUserId(String userId) {
        try {
            return hashIdsUtils.decode(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
