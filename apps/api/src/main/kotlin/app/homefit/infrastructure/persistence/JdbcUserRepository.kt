package app.homefit.infrastructure.persistence

import app.homefit.domain.user.User
import app.homefit.domain.user.UserRepository
import app.homefit.domain.user.UserUpsertInput
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class JdbcUserRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : UserRepository {

    private val userMapper = RowMapper { rs: ResultSet, _ ->
        User(
            id = rs.getLong("id"),
            email = rs.getString("email"),
            oauthProvider = rs.getString("oauth_provider"),
            oauthSubject = rs.getString("oauth_subject"),
            displayName = rs.getString("display_name"),
            profileImageUrl = rs.getString("profile_image_url"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            lastLoginAt = rs.getObject("last_login_at", OffsetDateTime::class.java),
        )
    }

    override fun upsert(input: UserUpsertInput): User {
        val sql = """
            INSERT INTO users (email, oauth_provider, oauth_subject, display_name, profile_image_url, last_login_at)
            VALUES (:email, :provider, :subject, :name, :image, now())
            ON CONFLICT (oauth_provider, oauth_subject) DO UPDATE SET
                email             = EXCLUDED.email,
                display_name      = COALESCE(EXCLUDED.display_name, users.display_name),
                profile_image_url = COALESCE(EXCLUDED.profile_image_url, users.profile_image_url),
                last_login_at     = now()
            RETURNING id, email, oauth_provider, oauth_subject, display_name,
                      profile_image_url, created_at, last_login_at
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("email", input.email)
            .addValue("provider", input.oauthProvider)
            .addValue("subject", input.oauthSubject)
            .addValue("name", input.displayName)
            .addValue("image", input.profileImageUrl)

        return jdbc.queryForObject(sql, params, userMapper)
            ?: error("upsert users returned null row")
    }

    override fun findById(id: Long): User? {
        val sql = """
            SELECT id, email, oauth_provider, oauth_subject, display_name,
                   profile_image_url, created_at, last_login_at
            FROM users
            WHERE id = :id
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("id", id), userMapper).firstOrNull()
    }

    override fun isEmailNotificationsEnabled(userId: Long): Boolean {
        return jdbc.queryForObject(
            "SELECT notification_email_enabled FROM users WHERE id = :id",
            MapSqlParameterSource("id", userId),
            Boolean::class.java,
        ) ?: true
    }

    override fun setEmailNotificationsEnabled(userId: Long, enabled: Boolean) {
        jdbc.update(
            "UPDATE users SET notification_email_enabled = :v WHERE id = :id",
            MapSqlParameterSource().addValue("id", userId).addValue("v", enabled),
        )
    }

    override fun isPushNotificationsEnabled(userId: Long): Boolean {
        return jdbc.queryForObject(
            "SELECT notification_push_enabled FROM users WHERE id = :id",
            MapSqlParameterSource("id", userId),
            Boolean::class.java,
        ) ?: true
    }

    override fun setPushNotificationsEnabled(userId: Long, enabled: Boolean) {
        jdbc.update(
            "UPDATE users SET notification_push_enabled = :v WHERE id = :id",
            MapSqlParameterSource().addValue("id", userId).addValue("v", enabled),
        )
    }
}
