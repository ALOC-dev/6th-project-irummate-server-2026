package com.irummate.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kakao")
@Getter
@Setter
public class KakaoProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String adminKey;
}
