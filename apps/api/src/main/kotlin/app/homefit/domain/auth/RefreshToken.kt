package app.homefit.domain.auth

import java.time.OffsetDateTime

data class RefreshToken(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

interface RefreshTokenRepository {
    fun save(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): Long
    fun findValidByHash(tokenHash: String): RefreshToken?
    fun revokeById(id: Long)
    fun revokeAllForUser(userId: Long)
}
