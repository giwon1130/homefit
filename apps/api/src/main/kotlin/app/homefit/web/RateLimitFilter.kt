package app.homefit.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Redis fixed-window 토큰 카운터 기반 rate limit.
 *  - 익명: IP 당 60 req/min
 *  - 인증: userId 당 300 req/min
 *  - /actuator/health, swagger 등은 면제
 *
 * Redis 다운 시 fail-open (요청 통과 + warn).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // RequestLoggingFilter 직후
class RateLimitFilter(
    private val redis: StringRedisTemplate,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI
        if (isExempt(path)) {
            filterChain.doFilter(request, response)
            return
        }

        val principal = SecurityContextHolder.getContext().authentication?.principal as? Long
        val (key, limit) =
            if (principal != null) "rl:u:$principal" to AUTH_LIMIT
            else "rl:ip:${clientIp(request)}" to ANON_LIMIT

        val count = runCatching { incrWithTtl(key) }
            .onFailure { log.warn("rate limit redis fail-open: {}", it.message) }
            .getOrNull()

        if (count != null && count > limit) {
            response.status = 429
            response.setHeader("Retry-After", "60")
            response.contentType = "application/json"
            response.writer.write("""{"error":"rate_limit_exceeded","limit":$limit}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun incrWithTtl(key: String): Long {
        val ops = redis.opsForValue()
        val v = ops.increment(key) ?: 0L
        if (v == 1L) {
            redis.expire(key, WINDOW)
        }
        return v
    }

    private fun clientIp(req: HttpServletRequest): String {
        val xff = req.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        return if (!xff.isNullOrBlank()) xff else req.remoteAddr ?: "unknown"
    }

    private fun isExempt(path: String): Boolean =
        path.startsWith("/actuator/health") ||
            path.startsWith("/swagger") ||
            path.startsWith("/v3/api-docs")

    companion object {
        private const val ANON_LIMIT = 60
        private const val AUTH_LIMIT = 300
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }
}
