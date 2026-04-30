package app.homefit.ingestion.scheduler

import app.homefit.ingestion.application.notification.NotificationDispatchService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 즐겨찾기 청약 D-1 이메일 알림 스케줄러.
 * 매시간 정각 5분에 실행 — 다른 ingestion job 들과 시간 분산.
 *
 * SMTP 미설정 환경(homefit.notification.email.enabled=false)에서는 dispatch 자체가 no-op.
 */
@Component
class D1NotificationJob(
    private val dispatch: NotificationDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Seoul")
    fun run() {
        runCatching { dispatch.dispatchD1() }
            .onSuccess {
                if (it.candidates > 0) {
                    log.info(
                        "d1 dispatch ok candidates={} email(sent={}/failed={}/skipped={}) push(sent={}/failed={}/skipped={})",
                        it.candidates,
                        it.emailSent, it.emailFailed, it.emailSkipped,
                        it.pushSent, it.pushFailed, it.pushSkipped,
                    )
                }
            }
            .onFailure { log.error("d1 dispatch failed", it) }
    }
}
