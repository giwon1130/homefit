package app.homefit.web.security

import app.homefit.application.auth.JwtService
import io.sentry.Sentry
import io.sentry.protocol.User as SentryUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val userId = jwtService.verifyAccessToken(token)
            if (userId != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    userId, null, listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                SecurityContextHolder.getContext().authentication = auth
                // Sentry 이벤트가 발생하면 어떤 사용자인지만 식별 가능하게 (PII 최소).
                Sentry.setUser(SentryUser().apply { this.id = userId.toString() })
            }
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 요청 단위 user 정리 (다음 요청에 누설 방지).
            Sentry.setUser(null)
        }
    }
}
