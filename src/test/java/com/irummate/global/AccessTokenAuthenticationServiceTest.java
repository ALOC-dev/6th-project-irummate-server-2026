package com.irummate.global;

import com.irummate.domain.auth.repository.LoginSessionRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.security.AccessTokenAuthenticationService;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenAuthenticationServiceTest {
    @ParameterizedTest
    @CsvSource({"ACTIVE,true", "BANNED,false", "WITHDRAWN,false"})
    void preservesExistingWebSocketActiveAccountPolicy(UserStatus status, boolean allowed) {
        var repository = mock(LoginSessionRepository.class);
        var service = new AccessTokenAuthenticationService(mock(JwtTokenProvider.class), mock(HashIdsUtils.class), repository);
        var session = mock(LoginSessionRepository.SessionAuthentication.class);
        UUID id = UUID.randomUUID();
        when(repository.findAuthenticationById(id)).thenReturn(Optional.of(session));
        when(session.getExpiresAt()).thenReturn(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1));
        when(session.getUserId()).thenReturn(1L);
        when(session.getStatus()).thenReturn(status);
        when(session.getRole()).thenReturn(UserRole.ADMIN);

        if (allowed) {
            assertThat(service.validateSession(1L, id, Instant.now().plusSeconds(60)).role())
                    .isEqualTo(UserRole.ADMIN);
        } else {
            assertThatThrownBy(() -> service.validateSession(1L, id, Instant.now().plusSeconds(60)))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }
}
