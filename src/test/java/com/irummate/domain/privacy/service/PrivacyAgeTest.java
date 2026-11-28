package com.irummate.domain.privacy.service;

import com.irummate.domain.user.dto.UserDetailsRequestDto;
import com.irummate.domain.user.dto.UserDetailsUpdateRequestDto;
import com.irummate.domain.user.service.UserDetailsService;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivacyAgeTest {
    @Mock UsersRepository users;
    @InjectMocks UserDetailsService details;

    @Test
    void underageCreateAndUpdateAreRejectedBeforeDatabaseAccess() {
        var create = new UserDetailsRequestDto();
        ReflectionTestUtils.setField(create, "age", 13);
        assertEquals(ErrorCode.AGE_REQUIREMENT_NOT_MET, assertThrows(BusinessException.class,
                () -> details.createDetails(1L, create)).getErrorCode());
        var update = new UserDetailsUpdateRequestDto();
        ReflectionTestUtils.setField(update, "age", 13);
        assertEquals(ErrorCode.AGE_REQUIREMENT_NOT_MET, assertThrows(BusinessException.class,
                () -> details.updateDetails(1L, update)).getErrorCode());
        verifyNoInteractions(users);
    }

    @Test
    void fourteenPassesAgeGateAndProceedsToMemberLookup() {
        var create = new UserDetailsRequestDto();
        ReflectionTestUtils.setField(create, "age", 14);
        assertEquals(ErrorCode.USER_NOT_FOUND, assertThrows(BusinessException.class,
                () -> details.createDetails(1L, create)).getErrorCode());
        verify(users).findByIdForUpdate(1L);
    }

}
