package app.homefit.ingestion.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ListingIngestionJob {
    private val log = LoggerFactory.getLogger(javaClass)

    // 6시간마다 청약 공고 동기화 (Phase 1 에서 실제 구현 연결)
    @Scheduled(cron = "0 0 */6 * * *", zone = "Asia/Seoul")
    fun syncListings() {
        log.info("listing ingestion tick — implementation pending (Phase 1)")
    }
}
