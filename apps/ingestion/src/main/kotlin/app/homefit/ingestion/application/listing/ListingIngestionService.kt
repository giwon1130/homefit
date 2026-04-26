package app.homefit.ingestion.application.listing

import app.homefit.ingestion.config.LhProperties
import app.homefit.ingestion.config.PublicDataProperties
import app.homefit.ingestion.domain.listing.ListingSource
import app.homefit.ingestion.domain.listing.RawListing
import app.homefit.ingestion.infrastructure.kakao.Geocoder
import app.homefit.ingestion.infrastructure.lh.LhClient
import app.homefit.ingestion.infrastructure.lh.LhMapper
import app.homefit.ingestion.infrastructure.persistence.IngestionRunRepository
import app.homefit.ingestion.infrastructure.persistence.ListingRepository
import app.homefit.ingestion.infrastructure.publicdata.ApplyhomeMapper
import app.homefit.ingestion.infrastructure.publicdata.PublicDataClient
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptModelItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

data class IngestionResult(val pages: Int, val upserted: Int)
data class GeocodeResult(val attempted: Int, val succeeded: Int)

@Service
class ListingIngestionService(
    private val client: PublicDataClient,
    private val mapper: ApplyhomeMapper,
    private val listings: ListingRepository,
    private val runs: IngestionRunRepository,
    private val props: PublicDataProperties,
    private val geocoder: Geocoder,
    private val lhClient: LhClient,
    private val lhMapper: LhMapper,
    private val lhProps: LhProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 공공데이터포털 청약홈 APT 분양정보를 모집공고일 기준 최근 N일만 가져와 upsert.
     *
     * 주택형별(getAPTLttotPblancMdl) 응답은 HOUSE_MANAGE_NO 로 공고 단위 별도 조회 (Mdl 엔드포인트는 날짜 필터 미지원).
     * 새 공고가 들어오면 카카오 Local API 로 좌표를 1회 채움. 실패해도 listing 자체는 저장.
     */
    fun syncApt(lookbackDays: Long = props.lookbackDays): IngestionResult {
        val runId = runs.start(ListingSource.PUBLIC_DATA_APT.code)
        return try {
            val from = LocalDate.now().minusDays(lookbackDays)
            var page = 1
            var pages = 0
            var upserted = 0
            while (true) {
                val resp = client.fetchAptListings(page, from)
                pages++
                if (resp.data.isEmpty()) break
                for (item in resp.data) {
                    val key = item.houseManageNo ?: item.pblancNo ?: continue
                    val models = fetchModelsForListing(key)
                    val rawBase = mapper.toRawListing(item, models) ?: continue
                    val withGeo = enrichWithCoordinates(rawBase)
                    listings.upsert(withGeo)
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

    /**
     * LH 분양임대공고 동기화 (행복주택, 공공분양, 신혼희망타운, 임대 등).
     * 목록 endpoint 는 단지명/유형/지역/날짜만 줌. 주소·세부는 후속 PR (상세 endpoint)에서 enrich.
     */
    fun syncLh(lookbackDays: Long = lhProps.lookbackDays): IngestionResult {
        val runId = runs.start(ListingSource.LH.code)
        return try {
            val from = LocalDate.now().minusDays(lookbackDays)
            val to = LocalDate.now().plusDays(180)
            var page = 1
            var pages = 0
            var upserted = 0
            while (true) {
                val notices = lhClient.fetchNotices(page, lhProps.pageSize, from, to)
                pages++
                if (notices.isEmpty()) break
                for (n in notices) {
                    val raw = lhMapper.toRawListing(n) ?: continue
                    listings.upsert(raw)
                    upserted++
                }
                if (notices.size < lhProps.pageSize) break
                page++
                if (page > 50) { log.warn("LH page guard tripped"); break }
            }
            runs.succeed(runId, pages, upserted)
            log.info("LH sync done pages={} upserted={}", pages, upserted)
            IngestionResult(pages, upserted)
        } catch (e: Exception) {
            log.error("LH sync failed", e)
            runs.fail(runId, e.toString())
            throw e
        }
    }

    /** 주소 → 좌표 1회 호출. 실패해도 listing 자체는 저장. */
    private fun enrichWithCoordinates(raw: RawListing): RawListing =
        raw.address?.let { geocoder.geocode(it) }?.let {
            raw.copy(latitude = it.latitude, longitude = it.longitude)
        } ?: raw

    /** 기존에 좌표 없는 listing 들 백필. 한 번 호출에 limit 건만 처리 (rate 보호). */
    fun backfillCoordinates(limit: Int = 50): GeocodeResult {
        val pending = listings.findIdsAndAddressesWithoutGeo(limit)
        var success = 0
        for ((id, address) in pending) {
            val coords = geocoder.geocode(address) ?: continue
            listings.updateCoordinates(id, coords.latitude, coords.longitude)
            success++
        }
        log.info("geocode backfill attempted={} succeeded={}", pending.size, success)
        return GeocodeResult(pending.size, success)
    }

    /**
     * 공고 한 건의 주택형(평형) 목록. Mdl 엔드포인트는 날짜 필터가 없으므로
     * HOUSE_MANAGE_NO 로 직접 조회. 한 공고당 보통 3~10건이라 1페이지면 끝.
     */
    private fun fetchModelsForListing(houseManageNo: String): List<ApplyhomeAptModelItem> {
        val all = mutableListOf<ApplyhomeAptModelItem>()
        var page = 1
        while (true) {
            val resp = client.fetchAptModels(page, houseManageNo)
            if (resp.data.isEmpty()) break
            all += resp.data
            if (resp.data.size < resp.perPage) break
            page++
            if (page > 20) {  // 안전장치
                log.warn("model fetch exceeded 20 pages for {}", houseManageNo)
                break
            }
        }
        return all
    }
}
