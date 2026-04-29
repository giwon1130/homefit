package app.homefit.ingestion.application.notification

import app.homefit.ingestion.config.EmailNotificationProperties
import app.homefit.ingestion.infrastructure.notification.EmailSender
import app.homefit.ingestion.infrastructure.notification.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class NotificationDispatchService(
    private val repo: NotificationRepository,
    private val email: EmailSender,
    private val props: EmailNotificationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class DispatchResult(
        val candidates: Int,
        val sent: Int,
        val failed: Int,
        val skipped: Int,    // unique idx 충돌 (이미 발송됨)
    )

    /**
     * 현재 시각 기준 [now+22h, now+26h) 윈도우 안에 마감되는 즐겨찾기 단지에
     * D-1 이메일 발송. 시간당 1회 호출되는 설계 — 4시간 윈도우는 안전 마진.
     *
     * unique idx 가 사용자 단위 중복을 차단하므로 윈도우가 겹쳐도 한 번만 발송됨.
     */
    fun dispatchD1(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): DispatchResult {
        if (!props.enabled) {
            log.info("email notifications disabled (homefit.notification.email.enabled=false) — skip")
            return DispatchResult(0, 0, 0, 0)
        }
        val from = now.plusHours(22)
        val to = now.plusHours(26)
        val pending = repo.findPendingD1(from, to)
        if (pending.isEmpty()) {
            log.debug("no pending D-1 notifications in window {} -> {}", from, to)
            return DispatchResult(0, 0, 0, 0)
        }
        log.info("dispatching D-1 notifications: candidates={}", pending.size)

        var sent = 0
        var failed = 0
        var skipped = 0
        for (target in pending) {
            try {
                email.sendD1(target)
                val inserted = repo.logSent(
                    target.userId,
                    target.listingId,
                    NotificationKind.D_MINUS_1,
                    NotificationChannel.EMAIL,
                )
                if (inserted) sent++ else skipped++
            } catch (e: Exception) {
                log.warn(
                    "d1 email failed user={} listing={} reason={}",
                    target.userEmail, target.listingId, e.message,
                )
                repo.logFailed(
                    target.userId,
                    target.listingId,
                    NotificationKind.D_MINUS_1,
                    NotificationChannel.EMAIL,
                    e.message ?: e::class.simpleName.orEmpty(),
                )
                failed++
            }
        }
        return DispatchResult(pending.size, sent, failed, skipped)
    }
}
