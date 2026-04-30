package app.homefit.ingestion.infrastructure.notification

import app.homefit.ingestion.application.notification.NotificationChannel
import app.homefit.ingestion.application.notification.NotificationKind
import app.homefit.ingestion.application.notification.NotificationStatus
import app.homefit.ingestion.application.notification.PendingD1Notification
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class NotificationRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    /**
     * applicationEnd 가 [from, to) 윈도우 안에 있는 즐겨찾기를 가진 유저 + 단지 페어.
     * 채널별 발송 여부는 디스패처에서 결정 — 여기서는 사용자의 enabled 플래그까지만 노출.
     *
     * 이미 SENT 인 (user, listing, kind, channel) 조합은 INSERT 단계의 unique idx 가 막아주므로
     * 여기서는 채널별 LEFT JOIN 으로 거르지 않고, 단지 후보를 모두 반환.
     */
    fun findPendingD1(from: OffsetDateTime, to: OffsetDateTime): List<PendingD1Notification> {
        val sql = """
            SELECT u.id           AS user_id,
                   u.email        AS user_email,
                   u.display_name AS user_display_name,
                   u.notification_email_enabled AS email_enabled,
                   u.notification_push_enabled  AS push_enabled,
                   l.id           AS listing_id,
                   l.name         AS listing_name,
                   l.listing_type AS listing_type,
                   l.application_end AS application_end
              FROM favorites f
              JOIN users u    ON u.id = f.user_id
              JOIN listings l ON l.id = f.listing_id
             WHERE l.application_end IS NOT NULL
               AND l.application_end >= :from
               AND l.application_end <  :to
               -- 적어도 한 채널은 켜져있어야 후보가 됨.
               AND (u.notification_email_enabled OR u.notification_push_enabled)
             ORDER BY l.application_end ASC, u.id ASC
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("from", from)
            .addValue("to", to)
        return jdbc.query(sql, params) { rs, _ ->
            PendingD1Notification(
                userId = rs.getLong("user_id"),
                userEmail = rs.getString("user_email"),
                userDisplayName = rs.getString("user_display_name"),
                emailEnabled = rs.getBoolean("email_enabled"),
                pushEnabled = rs.getBoolean("push_enabled"),
                listingId = rs.getLong("listing_id"),
                listingName = rs.getString("listing_name"),
                listingType = rs.getString("listing_type"),
                applicationEnd = rs.getObject("application_end", OffsetDateTime::class.java),
            )
        }
    }

    /** 사용자의 활성 푸시 토큰 목록. */
    fun findPushTokens(userId: Long): List<String> {
        return jdbc.query(
            "SELECT token FROM push_tokens WHERE user_id = :uid ORDER BY last_seen_at DESC",
            MapSqlParameterSource("uid", userId),
        ) { rs, _ -> rs.getString(1) }
    }

    /** 같은 (user, listing, kind, channel) 이 이미 SENT 상태인지 빠른 확인. */
    fun isAlreadyLogged(
        userId: Long,
        listingId: Long,
        kind: NotificationKind,
        channel: NotificationChannel,
    ): Boolean {
        return jdbc.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM notifications
                 WHERE user_id = :uid AND listing_id = :lid
                   AND kind = :kind AND channel = :ch
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("lid", listingId)
                .addValue("kind", kind.name)
                .addValue("ch", channel.name),
            Boolean::class.java,
        ) ?: false
    }

    fun logSent(userId: Long, listingId: Long, kind: NotificationKind, channel: NotificationChannel): Boolean {
        return tryInsert(userId, listingId, kind, channel, NotificationStatus.SENT, null)
    }

    fun logFailed(userId: Long, listingId: Long, kind: NotificationKind, channel: NotificationChannel, error: String): Boolean {
        return tryInsert(userId, listingId, kind, channel, NotificationStatus.FAILED, error)
    }

    private fun tryInsert(
        userId: Long,
        listingId: Long,
        kind: NotificationKind,
        channel: NotificationChannel,
        status: NotificationStatus,
        error: String?,
    ): Boolean {
        val sql = """
            INSERT INTO notifications (user_id, listing_id, kind, channel, status, error_text)
            VALUES (:uid, :lid, :kind, :ch, :st, :err)
            ON CONFLICT (user_id, listing_id, kind, channel) DO NOTHING
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("uid", userId)
            .addValue("lid", listingId)
            .addValue("kind", kind.name)
            .addValue("ch", channel.name)
            .addValue("st", status.name)
            .addValue("err", error?.take(2000))
        return try {
            jdbc.update(sql, params) > 0
        } catch (e: DuplicateKeyException) {
            false
        }
    }
}
