package app.homefit.ingestion.scheduler

import app.homefit.ingestion.application.listing.ListingIngestionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ListingIngestionJob(
    private val service: ListingIngestionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 6시간마다 청약홈 APT 분양정보 동기화. */
    @Scheduled(cron = "0 0 */6 * * *", zone = "Asia/Seoul")
    fun syncAptListings() {
        log.info("scheduled apt ingestion starting")
        runCatching { service.syncApt() }
            .onSuccess { log.info("scheduled apt ingestion ok pages={} upserted={}", it.pages, it.upserted) }
            .onFailure { log.error("scheduled apt ingestion failed", it) }
    }

    /** 8시간마다 LH 공고 동기화 (행복주택/공공/임대 등). APT 와 시간 어긋나게. */
    @Scheduled(cron = "0 30 */8 * * *", zone = "Asia/Seoul")
    fun syncLh() {
        log.info("scheduled lh ingestion starting")
        runCatching { service.syncLh() }
            .onSuccess { log.info("scheduled lh ingestion ok pages={} upserted={}", it.pages, it.upserted) }
            .onFailure { log.error("scheduled lh ingestion failed", it) }
    }
}
