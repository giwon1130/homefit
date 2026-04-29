package app.homefit.ingestion.application.notification

import java.time.OffsetDateTime

/**
 * D-1 알림 발송 대상 한 건. favorites JOIN listings JOIN users 결과.
 */
data class PendingD1Notification(
    val userId: Long,
    val userEmail: String,
    val userDisplayName: String?,
    val listingId: Long,
    val listingName: String,
    val listingType: String,
    val applicationEnd: OffsetDateTime,
)

enum class NotificationKind { D_MINUS_1 }
enum class NotificationChannel { EMAIL }
enum class NotificationStatus { SENT, FAILED }
