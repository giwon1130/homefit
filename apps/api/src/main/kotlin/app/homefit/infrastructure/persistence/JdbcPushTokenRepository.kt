package app.homefit.infrastructure.persistence

import app.homefit.domain.notification.PushPlatform
import app.homefit.domain.notification.PushToken
import app.homefit.domain.notification.PushTokenRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JdbcPushTokenRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : PushTokenRepository {

    private val mapper = RowMapper { rs, _ ->
        PushToken(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            platform = PushPlatform.valueOf(rs.getString("platform")),
            token = rs.getString("token"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            lastSeenAt = rs.getObject("last_seen_at", OffsetDateTime::class.java),
        )
    }

    override fun upsert(userId: Long, platform: PushPlatform, token: String): PushToken {
        // token 은 unique. 다른 유저에 매핑돼있으면 user_id 까지 덮어써서 새 유저로 옮김.
        val sql = """
            INSERT INTO push_tokens (user_id, platform, token)
            VALUES (:uid, :pf, :tk)
            ON CONFLICT (token) DO UPDATE SET
                user_id      = EXCLUDED.user_id,
                platform     = EXCLUDED.platform,
                last_seen_at = now()
            RETURNING id, user_id, platform, token, created_at, last_seen_at
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("uid", userId)
            .addValue("pf", platform.name)
            .addValue("tk", token)
        return jdbc.queryForObject(sql, params, mapper)
            ?: error("upsert push_tokens returned null")
    }

    override fun deleteByToken(token: String): Int {
        return jdbc.update(
            "DELETE FROM push_tokens WHERE token = :tk",
            MapSqlParameterSource("tk", token),
        )
    }

    override fun findActiveByUserId(userId: Long): List<PushToken> {
        return jdbc.query(
            """
            SELECT id, user_id, platform, token, created_at, last_seen_at
              FROM push_tokens
             WHERE user_id = :uid
             ORDER BY last_seen_at DESC
            """.trimIndent(),
            MapSqlParameterSource("uid", userId),
            mapper,
        )
    }
}
