package app.homefit.ingestion.application.notification

import app.homefit.ingestion.config.EmailNotificationProperties
import app.homefit.ingestion.infrastructure.notification.EmailSender
import app.homefit.ingestion.infrastructure.notification.ExpoPushSender
import app.homefit.ingestion.infrastructure.notification.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 알림 디스패처. 두 종류:
 *   1) D_MINUS_1 — 청약 접수 마감 D-1 (시간당 cron, 22~26h 윈도우)
 *   2) RESULT_D_MINUS_1 — 당첨자 발표 D-1 (매일 09시 cron, 내일 1일 윈도우)
 *
 * 채널 (이메일 + 푸시) 양쪽 발송. 채널별 토글 OFF / unique idx 충돌 / SMTP disabled
 * 모두 SKIP. 한 채널 실패해도 다른 채널은 시도.
 */
@Service
class NotificationDispatchService(
    private val repo: NotificationRepository,
    private val email: EmailSender,
    private val push: ExpoPushSender,
    private val emailProps: EmailNotificationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class DispatchResult(
        val candidates: Int,
        val emailSent: Int,
        val emailFailed: Int,
        val emailSkipped: Int,   // unique idx 충돌 / disabled / props 미설정
        val pushSent: Int,
        val pushFailed: Int,
        val pushSkipped: Int,
    ) {
        companion object { val ZERO = DispatchResult(0, 0, 0, 0, 0, 0, 0) }
    }

    /** 청약 접수 마감 D-1. now+22h~now+26h 윈도우 (시간당 cron 안전 마진). */
    fun dispatchD1(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): DispatchResult {
        val pending = repo.findPendingD1(now.plusHours(22), now.plusHours(26))
        return dispatchKind(
            pending = pending,
            kind = NotificationKind.D_MINUS_1,
            emailFn = email::sendD1,
            pushFn = push::sendD1,
        )
    }

    /** 당첨자 발표 D-1. 매일 1회 (09시 KST 권장). 내일 발표인 즐겨찾기 단지. */
    fun dispatchResultD1(today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))): DispatchResult {
        val pending = repo.findPendingResultD1(today.plusDays(1), today.plusDays(2))
        return dispatchKind(
            pending = pending,
            kind = NotificationKind.RESULT_D_MINUS_1,
            emailFn = email::sendResultD1,
            pushFn = push::sendResultD1,
        )
    }

    /**
     * 채널 발송 + 로그 + 카운트. kind 는 unique idx 차단·로그용.
     */
    private fun dispatchKind(
        pending: List<PendingD1Notification>,
        kind: NotificationKind,
        emailFn: (PendingD1Notification) -> Unit,
        pushFn: (String, PendingD1Notification) -> Unit,
    ): DispatchResult {
        if (pending.isEmpty()) {
            log.debug("no pending {} notifications", kind)
            return DispatchResult.ZERO
        }

        var emailSent = 0; var emailFailed = 0; var emailSkipped = 0
        var pushSent = 0; var pushFailed = 0; var pushSkipped = 0

        for (target in pending) {
            // ---- 이메일 ----
            if (!emailProps.enabled || !target.emailEnabled ||
                repo.isAlreadyLogged(target.userId, target.listingId, kind, NotificationChannel.EMAIL)
            ) {
                emailSkipped++
            } else {
                try {
                    emailFn(target)
                    val inserted = repo.logSent(target.userId, target.listingId, kind, NotificationChannel.EMAIL)
                    if (inserted) emailSent++ else emailSkipped++
                } catch (e: Exception) {
                    log.warn("email {} failed user={} listing={} reason={}",
                        kind, target.userEmail, target.listingId, e.message)
                    repo.logFailed(target.userId, target.listingId, kind, NotificationChannel.EMAIL,
                        e.message ?: e::class.simpleName.orEmpty())
                    emailFailed++
                }
            }

            // ---- 푸시 ----
            if (!target.pushEnabled ||
                repo.isAlreadyLogged(target.userId, target.listingId, kind, NotificationChannel.PUSH)
            ) {
                pushSkipped++
                continue
            }
            val tokens = repo.findPushTokens(target.userId)
            if (tokens.isEmpty()) {
                pushSkipped++
                continue
            }
            // 여러 디바이스 — 하나라도 성공하면 SENT, 전부 실패면 FAILED.
            var anySuccess = false
            var lastError: String? = null
            for (token in tokens) {
                try {
                    pushFn(token, target)
                    anySuccess = true
                } catch (e: Exception) {
                    log.warn("push {} failed user={} listing={} token=...{} reason={}",
                        kind, target.userId, target.listingId, token.takeLast(8), e.message)
                    lastError = e.message ?: e::class.simpleName.orEmpty()
                }
            }
            if (anySuccess) {
                val inserted = repo.logSent(target.userId, target.listingId, kind, NotificationChannel.PUSH)
                if (inserted) pushSent++ else pushSkipped++
            } else {
                repo.logFailed(target.userId, target.listingId, kind, NotificationChannel.PUSH,
                    lastError ?: "all tokens failed")
                pushFailed++
            }
        }
        return DispatchResult(
            candidates = pending.size,
            emailSent = emailSent, emailFailed = emailFailed, emailSkipped = emailSkipped,
            pushSent = pushSent, pushFailed = pushFailed, pushSkipped = pushSkipped,
        )
    }
}
