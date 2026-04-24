package app.homefit.application.auth

import app.homefit.config.AuthProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtService(props: AuthProperties) {
    private val key: SecretKey = Keys.hmacShaKeyFor(props.jwtSecret.toByteArray(Charsets.UTF_8))
    private val accessTtlMinutes = props.accessTokenTtlMinutes
    private val issuer = "homefit"

    fun issueAccessToken(userId: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact()
    }

    /** 유효하면 userId 반환, 아니면 null. */
    fun verifyAccessToken(token: String): Long? = runCatching {
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
            .toLong()
    }.getOrNull()
}
