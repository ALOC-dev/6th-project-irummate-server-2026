package com.irummate.global.util;

import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HashIdsUtils {

    private final Hashids hashids;

    public HashIdsUtils(@Value("${hashids.salt}") String salt,
                         @Value("${hashids.minlength}") String length) {
        this.hashids = new Hashids(salt, Integer.parseInt(length));
    }

    public String encode(Long id) {
        if (id == null) {
            return null;
        }

        return hashids.encode(id);
    }

    public Long decode(String hashId) {
        if (hashId == null || hashId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "식별자가 비어 있습니다.");
        }

        long[] decoded = hashids.decode(hashId);
        if (decoded.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 식별자입니다.");
        }

        return decoded[0];
    }
}
