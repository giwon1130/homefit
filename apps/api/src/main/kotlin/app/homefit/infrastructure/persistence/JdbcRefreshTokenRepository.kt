package app.homefit.infrastructure.persistence

import app.homefit.domain.auth.RefreshToken
import app.homefit.domain.auth.RefreshTokenRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class JdbcRefreshTokenRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : RefreshTokenRepository {

    private val mapper = RowMapper { rs: ResultSet, _ ->
        RefreshToken(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            tokenHash = rs.getString("token_hash"),
            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
            revokedAt = rs.getObject("revoked_at", OffsetDateTime::class.java),
        )
    }

    override fun save(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): Long {
        val sql = """
            INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
            VALUES (:user_id, :hash, :expires_at) RETURNING id
        """.trimIndent()
        val keys = GeneratedKeyHolder()
        jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("hash", tokenHash)
                .addValue("expires_at", expiresAt),
            keys, arrayOf("id"),
        )
        return keys.key?.toLong() ?: error("failed to save refresh token")
    }

    override fun findValidByHash(tokenHash: String): RefreshToken? {
        val sql = """
            SELECT id, user_id, token_hash, expires_at, revoked_at
            FROM refresh_tokens
            WHERE token_hash = :hash AND revoked_at IS NULL AND expires_at > now()
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("hash", tokenHash), mapper).firstOrNull()
    }

    override fun revokeById(id: Long) {
        jdbc.update(
            "UPDATE refresh_tokens SET revoked_at = now() WHERE id = :id AND revoked_at IS NULL",
            MapSqlParameterSource("id", id),
        )
    }

    override fun revokeAllForUser(userId: Long) {
        jdbc.update(
            "UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :uid AND revoked_at IS NULL",
            MapSqlParameterSource("uid", userId),
        )
    }
}
