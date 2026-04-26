package app.homefit.ingestion.infrastructure.lh

import app.homefit.ingestion.domain.listing.ListingSource
import app.homefit.ingestion.domain.listing.ListingType
import app.homefit.ingestion.domain.listing.RawListing
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class LhMapper(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val seoulZone = ZoneId.of("Asia/Seoul")
    private val dotFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val plainFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun toRawListing(notice: LhClient.LhNotice): RawListing? {
        val sourceRef = notice.panId
        val name = notice.panName
        if (sourceRef.isNullOrBlank() || name.isNullOrBlank()) {
            log.debug("skip LH notice: missing PAN_ID or PAN_NM ({})", notice)
            return null
        }
        return RawListing(
            source = ListingSource.LH,
            sourceRef = sourceRef,
            listingType = classify(notice.houseTypeName, notice.upperTypeName),
            name = name,
            developer = "한국토지주택공사",
            sido = notice.sido,
            sigungu = null,                       // 목록 응답에는 없음. 상세 호출로 후속 보강.
            address = null,
            applicationStart = parseDot(notice.noticeStart, startOfDay = true),
            applicationEnd = parseDot(notice.closingDate, startOfDay = false),
            announcementDate = parsePlain(notice.noticeDate),
            winnerAnnouncementDate = null,
            contractStartDate = null,
            contractEndDate = null,
            moveInDate = null,
            totalSupply = null,
            documentUrl = notice.detailUrl,
            rawJson = objectMapper.writeValueAsString(notice),
            units = emptyList(),
        )
    }

    /** AIS_TP_CD_NM (세부) 우선, 없으면 UPP_AIS_TP_NM (상위). */
    private fun classify(detail: String?, upper: String?): ListingType {
        val d = (detail ?: "").trim()
        val u = (upper ?: "").trim()
        return when {
            "행복" in d -> ListingType.HAPPY_HOUSE
            "신혼희망" in d -> ListingType.NEWLYWED_HOPE
            "공공분양" in d -> ListingType.PUBLIC_SALE
            "매입" in d -> ListingType.PURCHASE_RENTAL
            "전세" in d -> ListingType.JEONSE_RENTAL
            "국민임대" in d -> ListingType.NATIONAL_RENTAL
            "영구임대" in d -> ListingType.NATIONAL_RENTAL
            "장기전세" in d -> ListingType.JEONSE_RENTAL
            "분양" in d || "분양" in u -> ListingType.PUBLIC_SALE
            "임대" in d || "임대" in u -> ListingType.NATIONAL_RENTAL
            else -> ListingType.OTHER
        }
    }

    private fun parseDot(raw: String?, startOfDay: Boolean): OffsetDateTime? {
        val date = raw?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it, dotFmt) }.getOrNull()
        } ?: return null
        val time = if (startOfDay) LocalTime.of(0, 0) else LocalTime.of(23, 59, 59)
        return LocalDateTime.of(date, time).atZone(seoulZone).toOffsetDateTime()
    }

    private fun parsePlain(raw: String?): LocalDate? =
        raw?.takeIf { it.length == 8 }?.let {
            runCatching { LocalDate.parse(it, plainFmt) }.getOrNull()
        }
}
