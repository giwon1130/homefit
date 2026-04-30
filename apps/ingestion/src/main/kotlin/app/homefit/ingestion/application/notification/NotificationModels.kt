package app.homefit.ingestion.application.notification

import java.time.OffsetDateTime

/**
 * D-1 알림 발송 대상 한 건. favorites JOIN listings JOIN users 결과.
 * 채널별 enabled 플래그도 함께 가져와서 디스패처가 채널 선택.
 */
data class PendingD1Notification(
    val userId: Long,
    val userEmail: String,
    val userDisplayName: String?,
    val emailEnabled: Boolean,
    val pushEnabled: Boolean,
    val listingId: Long,
    val listingName: String,
    val listingType: String,
    val applicationEnd: OffsetDateTime,
)

enum class NotificationKind { D_MINUS_1 }
enum class NotificationChannel { EMAIL, PUSH }
enum class NotificationStatus { SENT, FAILED }
