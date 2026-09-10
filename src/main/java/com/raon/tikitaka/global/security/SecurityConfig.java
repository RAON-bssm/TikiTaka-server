package com.raon.tikitaka.global.security;

import com.raon.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import com.raon.tikitaka.global.security.jwt.JwtProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;


@Configuration
@EnableMethodSecurity
public class SecurityConfig {


    @Value("${cors.allowed-origins:}")
    private List<String> extraOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())

                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/login/**", "/api/auth/signup", "/api/auth/refresh").permitAll()
                        // 닉네임 중복확인. 가입 화면에서 토큰 발급 전에 호출된다
                        .requestMatchers(HttpMethod.GET, "/api/auth/check-name").permitAll()
                        // /api/location: 가입 화면의 동네 선택 목록. 토큰 발급 전에 호출된다
                        // /api/location/rank: 지역 랭킹. principal을 쓰지 않는 전체 공용 데이터다
                        // 둘 다 GET만 열어둔다. 같은 경로의 쓰기 요청은 계속 인증이 필요하다
                        .requestMatchers(HttpMethod.GET, "/api/location", "/api/location/rank").permitAll()
                        // 비로그인 둘러보기: 진행 중인 게시판 목록과 그 안의 게시물 조회
                        // GET만 열려 있으므로 작성/수정/삭제(POST, PATCH)는 그대로 인증이 필요하다
                        .requestMatchers(HttpMethod.GET, "/api/board", "/api/post/**").permitAll()
                        // 게시물의 postImage는 URL이 아니라 S3 key라서, 비로그인 조회를 허용하려면
                        // 서명 URL 발급도 같이 열려야 한다. upload-url(쓰기 권한 발급)은 계속 인증이 필요하다
                        .requestMatchers(HttpMethod.GET, "/api/storage/view-url").permitAll()
                        // 관리자 API는 JWT의 role이 ADMIN인 유저만. 필터가 ROLE_ADMIN 권한을 심어준다
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // 이 설정이 없으면 httpBasic/formLogin을 모두 disable한 탓에 기본값인
                // Http403ForbiddenEntryPoint가 쓰여서, 토큰 만료도 401이 아닌 403 + 스프링 기본 에러 바디로 나간다
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource  corsConfigurationSource() {
        List<String> patterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        patterns.addAll(extraOrigins);
        patterns.removeIf(String::isBlank);   // 환경변수가 비었을 때 들어오는 빈 문자열 제거

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
