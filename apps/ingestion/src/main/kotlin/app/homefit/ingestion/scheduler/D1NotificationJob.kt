package app.homefit.ingestion.scheduler

import app.homefit.ingestion.application.notification.NotificationDispatchService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 즐겨찾기 청약 알림 스케줄러.
 *  - 접수 마감 D-1: 매시간 정각 5분 (시간 단위 윈도우)
 *  - 당첨자 발표 D-1: 매일 09시 5분 (날짜 단위 윈도우, 발표일 자정 기준)
 *
 * SMTP 미설정 환경(homefit.notification.email.enabled=false)에서는
 * 이메일 채널만 SKIP (푸시는 그대로 동작).
 */
@Component
class D1NotificationJob(
    private val dispatch: NotificationDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Seoul")
    fun runApplicationD1() {
        runCatching { dispatch.dispatchD1() }
            .onSuccess { logResult("application-d1", it) }
            .onFailure { log.error("application-d1 dispatch failed", it) }
    }

    @Scheduled(cron = "0 5 9 * * *", zone = "Asia/Seoul")
    fun runResultD1() {
        runCatching { dispatch.dispatchResultD1() }
            .onSuccess { logResult("result-d1", it) }
            .onFailure { log.error("result-d1 dispatch failed", it) }
    }

    private fun logResult(label: String, r: NotificationDispatchService.DispatchResult) {
        if (r.candidates > 0) {
            log.info(
                "{} dispatch ok candidates={} email(sent={}/failed={}/skipped={}) push(sent={}/failed={}/skipped={})",
                label, r.candidates,
                r.emailSent, r.emailFailed, r.emailSkipped,
                r.pushSent, r.pushFailed, r.pushSkipped,
            )
        }
    }
}
