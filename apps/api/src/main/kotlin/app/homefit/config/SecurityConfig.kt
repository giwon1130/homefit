package app.homefit.config

import app.homefit.web.security.JwtAuthenticationFilter
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.http.HttpStatus

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // ERROR/ASYNC dispatch는 컨트롤러에서 던진 예외 처리 경로 — 무조건 허용
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/api/v1/ping",
                        "/api/v1/auth/google",
                        "/api/v1/auth/refresh",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                    // Auth required (먼저 매치되도록 위에 둠) — /listings/match, /listings/*/eligibility
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/listings/match",
                        "/api/v1/listings/*/eligibility",
                    ).authenticated()
                    // 그 외 listings GET은 공개
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/listings",
                        "/api/v1/listings/*",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
