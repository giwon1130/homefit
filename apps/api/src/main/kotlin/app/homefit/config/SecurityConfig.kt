package app.homefit.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/actuator/health",
                        "/api/v1/ping",
                        "/api/v1/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
        return http.build()
    }
}
