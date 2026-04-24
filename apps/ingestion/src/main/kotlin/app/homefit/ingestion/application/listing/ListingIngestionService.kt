package app.homefit.ingestion.application.listing

import app.homefit.ingestion.config.PublicDataProperties
import app.homefit.ingestion.domain.listing.ListingSource
import app.homefit.ingestion.infrastructure.persistence.IngestionRunRepository
import app.homefit.ingestion.infrastructure.persistence.ListingRepository
import app.homefit.ingestion.infrastructure.publicdata.ApplyhomeMapper
import app.homefit.ingestion.infrastructure.publicdata.PublicDataClient
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptModelItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

data class IngestionResult(val pages: Int, val upserted: Int)

@Service
class ListingIngestionService(
    private val client: PublicDataClient,
    private val mapper: ApplyhomeMapper,
    private val listings: ListingRepository,
    private val runs: IngestionRunRepository,
    private val props: PublicDataProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 공공데이터포털 청약홈 APT 분양정보를 모집공고일 기준 최근 N일만 가져와 upsert.
     *
     * 주택형별(getAPTLttotPblancMdl) 응답도 같은 범위로 미리 다 받아 HOUSE_MANAGE_NO 로 매칭.
     * 페이지 수가 수십 페이지 이하인 도메인이라 메모리 보관 수용 가능.
     */
    fun syncApt(lookbackDays: Long = props.lookbackDays): IngestionResult {
        val runId = runs.start(ListingSource.PUBLIC_DATA_APT.code)
        return try {
            val from = LocalDate.now().minusDays(lookbackDays)
            val models = fetchAllModels(from).groupBy { it.houseManageNo ?: it.pblancNo ?: "" }
            var page = 1
            var pages = 0
            var upserted = 0
            while (true) {
                val resp = client.fetchAptListings(page, from)
                pages++
                if (resp.data.isEmpty()) break
                for (item in resp.data) {
                    val key = item.houseManageNo ?: item.pblancNo ?: continue
                    val raw = mapper.toRawListing(item, models[key].orEmpty()) ?: continue
                    listings.upsert(raw)
                    upserted++
                }
                if (resp.data.size < resp.perPage) break
                page++
            }
            runs.succeed(runId, pages, upserted)
            log.info("applyhome apt sync done pages={} upserted={}", pages, upserted)
            IngestionResult(pages, upserted)
        } catch (e: Exception) {
            log.error("applyhome apt sync failed", e)
            runs.fail(runId, e.toString())
            throw e
        }
    }

    private fun fetchAllModels(from: LocalDate): List<ApplyhomeAptModelItem> {
        val all = mutableListOf<ApplyhomeAptModelItem>()
        var page = 1
        while (true) {
            val resp = client.fetchAptModels(page, from)
            if (resp.data.isEmpty()) break
            all += resp.data
            if (resp.data.size < resp.perPage) break
            page++
        }
        return all
    }
}
