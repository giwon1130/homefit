package app.homefit.ingestion.application.notification

import app.homefit.ingestion.config.EmailNotificationProperties
import app.homefit.ingestion.infrastructure.notification.EmailSender
import app.homefit.ingestion.infrastructure.notification.ExpoPushSender
import app.homefit.ingestion.infrastructure.notification.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * D-1 알림 디스패처. 채널 (이메일 + 푸시) 양쪽으로 발송.
 * - 채널별 사용자 토글이 OFF 면 해당 채널 스킵.
 * - 이미 전송 로그가 있는 (user, listing, kind, channel) 은 unique idx 가 차단.
 * - 한 채널 실패해도 다른 채널은 시도.
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
    )

    /**
     * 현재 시각 기준 [now+22h, now+26h) 윈도우 안에 마감되는 즐겨찾기 단지 후보를 모두 모아
     * 이메일/푸시 채널로 각각 발송. 시간당 1회 호출 — 4시간 윈도우는 안전 마진.
     */
    fun dispatchD1(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): DispatchResult {
        val from = now.plusHours(22)
        val to = now.plusHours(26)
        val pending = repo.findPendingD1(from, to)
        if (pending.isEmpty()) {
            log.debug("no pending D-1 notifications in window {} -> {}", from, to)
            return DispatchResult(0, 0, 0, 0, 0, 0, 0)
        }

        var emailSent = 0; var emailFailed = 0; var emailSkipped = 0
        var pushSent = 0; var pushFailed = 0; var pushSkipped = 0

        for (target in pending) {
            // ---- 이메일 ----
            if (!emailProps.enabled) {
                emailSkipped++
            } else if (!target.emailEnabled) {
                emailSkipped++
            } else if (repo.isAlreadyLogged(target.userId, target.listingId, NotificationKind.D_MINUS_1, NotificationChannel.EMAIL)) {
                emailSkipped++
            } else {
                try {
                    email.sendD1(target)
                    val inserted = repo.logSent(
                        target.userId, target.listingId,
                        NotificationKind.D_MINUS_1, NotificationChannel.EMAIL,
                    )
                    if (inserted) emailSent++ else emailSkipped++
                } catch (e: Exception) {
                    log.warn("email d1 failed user={} listing={} reason={}", target.userEmail, target.listingId, e.message)
                    repo.logFailed(target.userId, target.listingId, NotificationKind.D_MINUS_1, NotificationChannel.EMAIL, e.message ?: e::class.simpleName.orEmpty())
                    emailFailed++
                }
            }

            // ---- 푸시 ----
            if (!target.pushEnabled) {
                pushSkipped++
                continue
            }
            if (repo.isAlreadyLogged(target.userId, target.listingId, NotificationKind.D_MINUS_1, NotificationChannel.PUSH)) {
                pushSkipped++
                continue
            }
            val tokens = repo.findPushTokens(target.userId)
            if (tokens.isEmpty()) {
                pushSkipped++
                continue
            }

            // 한 사용자가 여러 디바이스 보유 가능 — 모두 발송. 한 디바이스라도 성공하면 SENT.
            var anySuccess = false
            var lastError: String? = null
            for (token in tokens) {
                try {
                    push.sendD1(token, target)
                    anySuccess = true
                } catch (e: Exception) {
                    log.warn("push d1 failed user={} listing={} token=...{} reason={}", target.userId, target.listingId, token.takeLast(8), e.message)
                    lastError = e.message ?: e::class.simpleName.orEmpty()
                }
            }
            if (anySuccess) {
                val inserted = repo.logSent(target.userId, target.listingId, NotificationKind.D_MINUS_1, NotificationChannel.PUSH)
                if (inserted) pushSent++ else pushSkipped++
            } else {
                repo.logFailed(target.userId, target.listingId, NotificationKind.D_MINUS_1, NotificationChannel.PUSH, lastError ?: "all tokens failed")
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
