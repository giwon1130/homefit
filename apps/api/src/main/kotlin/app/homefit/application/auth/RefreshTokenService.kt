package app.homefit.application.auth

import app.homefit.config.AuthProperties
import app.homefit.domain.auth.RefreshTokenRepository
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64

@Component
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val props: AuthProperties,
) {
    private val random = SecureRandom()

    data class Issued(val plaintext: String, val expiresAt: OffsetDateTime)

    fun issue(userId: Long): Issued {
        val bytes = ByteArray(48).also { random.nextBytes(it) }
        val plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = hash(plaintext)
        val expiresAt = OffsetDateTime.now().plusDays(props.refreshTokenTtlDays)
        repository.save(userId, hash, expiresAt)
        return Issued(plaintext, expiresAt)
    }

    /**
     * 기존 리프레시 토큰을 검증하고 회전.
     * 유효 → 이전 토큰 폐기 + 새 리프레시 토큰 발급.
     * 무효 → null.
     */
    fun rotate(plaintext: String): RotateResult? {
        val existing = repository.findValidByHash(hash(plaintext)) ?: return null
        repository.revokeById(existing.id)
        val issued = issue(existing.userId)
        return RotateResult(existing.userId, issued)
    }

    data class RotateResult(val userId: Long, val issued: Issued)

    private fun hash(plaintext: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
