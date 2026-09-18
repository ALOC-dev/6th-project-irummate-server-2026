package com.irummate.global.config;

import com.irummate.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
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
                "https://www.irummate.com",
                "https://irummate.com"
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())  //cors 추가
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // 무단 침입 발생 시 대처 요령 설정 (에러 핸들링)
                // 만약 통행증(토큰)도 없는 유저가 비밀 방(인증이 필요한 API)에 들어오려고 하다가 걸리면,
                // 조용히 401 Unauthorized(인증되지 않음) 에러를 발생시켜 내쫓으라고 지시합니다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // API별 통행 지정
                .authorizeHttpRequests(auth -> auth
                        // 아래에 적힌 주소들은 로그인 없이 누구나 '무사 통과(permitAll)'할 수 있도록 열어둠
                        .requestMatchers(
                                "/api/auth/kakao/**", // 카카오 로그인 관련 주소들
                                "/api/auth/refresh",   // 토큰 만료 시 재발급받는 주소
                                "/ws/chat/**",
                                "/error"                 // 에러 발생 시 리다이렉트되는 경로
                        ).permitAll()

                        // 그 외의 나머지 모든 요청(anyRequest)은
                        // 무조건 로그인(인증, authenticated)을 완료한 사람만 들어오게 철저히 막습니다.
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
