package com.irummate.global.config;

import com.irummate.global.jwt.JwtAuthenticationFilter;
import com.irummate.global.security.NoticeIngestKeyFilter;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.security.SecurityErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        //여기에 프론트 주소 추가하면 됨
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://www.irummate.com",
                "https://irummate.com",
                "capacitor://localhost",
                "https://localhost"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // 수집 경로만 API 키로 인증하고 일반 JWT 체인과 분리한다.
    @Bean
    @Order(1)
    public SecurityFilterChain noticeIngestSecurityFilterChain(HttpSecurity http,
            @Value("${notice.ingest-key:}") String key, SecurityErrorResponseWriter errorWriter) throws Exception {
        return http.securityMatcher("/internal/notices")
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new NoticeIngestKeyFilter(key, errorWriter), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorResponseWriter errorWriter) throws Exception {
        http

                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())  //cors 추가
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // 인증 실패와 권한 거절을 공통 오류 형식으로 반환
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorWriter.write(response, ErrorCode.FORBIDDEN))
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // API별 통행 지정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/notices").permitAll()
                        // 아래에 적힌 주소들은 로그인 없이 누구나 '무사 통과(permitAll)'할 수 있도록 열어둠
                        .requestMatchers(
                                "/api/auth/kakao/**", // 카카오 로그인 관련 주소들
                                "/api/auth/refresh",   // 토큰 만료 시 재발급받는 주소
                                "/api/auth/logout",
                                "/api/auth/status",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/ws/chat/**",
                                // 테스트 서버 Swagger
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/error"                 // 에러 발생 시 리다이렉트되는 경로
                        ).permitAll()
                        // 관리자 전용 API
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 일반 사용자 전용 API
                        .requestMatchers("/api/matching/**").hasRole("USER")
                        // 그 외의 나머지 모든 요청(anyRequest)은
                        // 무조건 로그인(인증, authenticated)을 완료한 사람만 들어오게 철저히 막습니다.
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    // JWT 필터는 SecurityFilterChain에서만 실행해 수집 API와 충돌하지 않게 한다.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
