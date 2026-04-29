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
     * 동일 (user, listing, kind) 알림이 이미 SENT 인 경우는 unique idx 가 INSERT 단계에서 막아줌
     * — 여기서 LEFT JOIN 으로 미리 거르면 윈도우 경계 race 가 단순해짐.
     */
    fun findPendingD1(from: OffsetDateTime, to: OffsetDateTime): List<PendingD1Notification> {
        val sql = """
            SELECT u.id           AS user_id,
                   u.email        AS user_email,
                   u.display_name AS user_display_name,
                   l.id           AS listing_id,
                   l.name         AS listing_name,
                   l.listing_type AS listing_type,
                   l.application_end AS application_end
              FROM favorites f
              JOIN users u    ON u.id = f.user_id
              JOIN listings l ON l.id = f.listing_id
              LEFT JOIN notifications n
                   ON n.user_id = f.user_id
                  AND n.listing_id = f.listing_id
                  AND n.kind = 'D_MINUS_1'
             WHERE u.notification_email_enabled = TRUE
               AND l.application_end IS NOT NULL
               AND l.application_end >= :from
               AND l.application_end <  :to
               AND n.id IS NULL
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
                listingId = rs.getLong("listing_id"),
                listingName = rs.getString("listing_name"),
                listingType = rs.getString("listing_type"),
                applicationEnd = rs.getObject("application_end", OffsetDateTime::class.java),
            )
        }
    }

    /**
     * 발송 결과 로그. 같은 (user, listing, kind) 가 이미 있으면 unique idx 위반 → 무시.
     * @return inserted 여부 (false 면 이미 존재 — 이중 발송 방지 성공).
     */
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
            ON CONFLICT (user_id, listing_id, kind) DO NOTHING
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
