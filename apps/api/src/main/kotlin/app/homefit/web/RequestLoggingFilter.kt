package app.homefit.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청별 MDC 컨텍스트(requestId, userId, method, path, status, durationMs) 채우고
 * 응답 후 액세스 로그 한 줄 출력. 구조화 로그(prod)에선 JSON 필드로 추출됨.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger("access")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader("X-Request-ID")
        val requestId = if (!incoming.isNullOrBlank()) incoming else UUID.randomUUID().toString().take(12)
        val start = System.currentTimeMillis()
        MDC.put("requestId", requestId)
        MDC.put("method", request.method)
        MDC.put("path", request.requestURI)
        try {
            filterChain.doFilter(request, response)
        } finally {
            // userId 는 인증 필터(JwtAuthenticationFilter) 이후 SecurityContext 에 자리잡음
            (SecurityContextHolder.getContext().authentication?.principal as? Long)?.let {
                MDC.put("userId", it.toString())
            }
            val duration = System.currentTimeMillis() - start
            MDC.put("status", response.status.toString())
            MDC.put("durationMs", duration.toString())
            response.setHeader("X-Request-ID", requestId)
            // 정적 자원/헬스체크는 로그 노이즈 줄이기
            val path = request.requestURI
            if (path != "/actuator/health" && !path.startsWith("/swagger") && !path.startsWith("/v3/api-docs")) {
                log.info("{} {} {} {}ms", request.method, path, response.status, duration)
            }
            MDC.clear()
        }
    }
}
