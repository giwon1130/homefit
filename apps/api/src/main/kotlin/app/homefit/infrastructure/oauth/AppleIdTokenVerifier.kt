package app.homefit.infrastructure.oauth

import app.homefit.config.AuthProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

data class AppleUserInfo(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
)

/**
 * Apple Sign In id_token 검증.
 *
 *  - JWKS: https://appleid.apple.com/auth/keys (Nimbus JWKSourceBuilder 의 자동 캐싱·갱신 사용)
 *  - 알고리즘: RS256
 *  - iss: https://appleid.apple.com
 *  - aud: AuthProperties.apple.audiences 중 하나와 일치 (보통 iOS bundle id)
 *
 * Apple 토큰의 email 은 처음 로그인할 때만 포함될 수 있으므로 nullable.
 * email_verified 가 true 인 경우만 신뢰 가능 — false / 부재시 거부.
 */
@Component
class AppleIdTokenVerifier(
    private val props: AuthProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val processor: DefaultJWTProcessor<SecurityContext>? = if (props.apple.audiences.isEmpty()) {
        null
    } else {
        val jwkSource = JWKSourceBuilder
            .create<SecurityContext>(URI("https://appleid.apple.com/auth/keys").toURL())
            .retrying(true)
            .build()
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
        }
    }

    val isEnabled: Boolean get() = processor != null

    /**
     * @return 유효한 토큰이면 AppleUserInfo, 아니면 null. 모든 실패 (만료/서명/aud 불일치) → null.
     */
    fun verify(idToken: String): AppleUserInfo? {
        val proc = processor ?: return null
        return try {
            val claims = proc.process(idToken, null)
            val iss = claims.issuer
            if (iss != "https://appleid.apple.com") {
                log.warn("apple token: invalid iss={}", iss)
                return null
            }
            val aud = claims.audience.orEmpty()
            if (aud.none { it in props.apple.audiences }) {
                log.warn("apple token: aud {} not in {}", aud, props.apple.audiences)
                return null
            }
            val sub = claims.subject ?: return null
            val email = claims.getStringClaim("email")
            val emailVerifiedClaim = claims.getClaim("email_verified")
            // email_verified 는 string("true") 또는 boolean 으로 올 수 있음 (Apple 사양).
            val emailVerified = when (emailVerifiedClaim) {
                is Boolean -> emailVerifiedClaim
                is String -> emailVerifiedClaim.equals("true", ignoreCase = true)
                else -> false
            }
            AppleUserInfo(subject = sub, email = email, emailVerified = emailVerified)
        } catch (e: Exception) {
            log.warn("apple token verify failed: {}", e.message)
            null
        }
    }
}
