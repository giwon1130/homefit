package app.homefit.ingestion.infrastructure.publicdata

import app.homefit.ingestion.domain.listing.ListingSource
import app.homefit.ingestion.domain.listing.ListingType
import app.homefit.ingestion.domain.listing.RawListing
import app.homefit.ingestion.domain.listing.RawListingUnit
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptItem
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptModelItem
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class ApplyhomeMapper(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    private val seoulZone = ZoneId.of("Asia/Seoul")

    fun toRawListing(item: ApplyhomeAptItem, modelsForItem: List<ApplyhomeAptModelItem>): RawListing? {
        val sourceRef = item.houseManageNo ?: item.pblancNo
        val name = item.houseName
        if (sourceRef.isNullOrBlank() || name.isNullOrBlank()) {
            log.warn("skip applyhome item: missing houseManageNo or houseName ({})", item)
            return null
        }

        val sido = item.areaName?.takeIf { it.isNotBlank() }
        val sigungu = parseSigunguFromAddress(item.address)

        return RawListing(
            source = ListingSource.PUBLIC_DATA_APT,
            sourceRef = sourceRef,
            listingType = ListingType.fromCheongyakHome(item.houseDetailTypeName, item.rentTypeName),
            name = name,
            developer = item.developer,
            sido = sido,
            sigungu = sigungu,
            address = item.address,
            applicationStart = parseApplicationDateTime(item.applicationStartDate, startOfDay = true),
            applicationEnd = parseApplicationDateTime(item.applicationEndDate, startOfDay = false),
            announcementDate = parseDate(item.announcementDate),
            winnerAnnouncementDate = parseDate(item.winnerAnnouncementDate),
            contractStartDate = parseDate(item.contractStartDate),
            contractEndDate = parseDate(item.contractEndDate),
            moveInDate = parseYearMonth(item.moveInYearMonth),
            totalSupply = item.totalSupply,
            documentUrl = item.documentUrl ?: item.homepageUrl,
            rawJson = objectMapper.writeValueAsString(item),
            units = modelsForItem.map { toUnit(it) },
        )
    }

    private fun toUnit(model: ApplyhomeAptModelItem): RawListingUnit = RawListingUnit(
        modelNo = model.modelNo,
        unitType = model.houseTy,
        sizeM2 = model.supplyArea?.let { runCatching { BigDecimal(it) }.getOrNull() },
        supplyCount = model.supplyCount,
        priceMaxKrw = model.topAmountThousandKrw?.let { it * 1_000L },
        rawJson = objectMapper.writeValueAsString(model),
    )

    private fun parseDate(raw: String?): LocalDate? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }

    private fun parseYearMonth(raw: String?): LocalDate? =
        raw?.takeIf { it.length == 6 }?.let {
            runCatching { LocalDate.parse("${it}01", DateTimeFormatter.ofPattern("yyyyMMdd")) }.getOrNull()
        }

    private fun parseApplicationDateTime(raw: String?, startOfDay: Boolean): OffsetDateTime? {
        val date = parseDate(raw) ?: return null
        val time = if (startOfDay) LocalDateTime.of(date, java.time.LocalTime.of(0, 0)) else LocalDateTime.of(date, java.time.LocalTime.of(23, 59, 59))
        return time.atZone(seoulZone).toOffsetDateTime()
    }

    /**
     * 공급위치(HSSPLY_ADRES) 예: "서울특별시 서초구 반포동 123-4".
     * 2번째 토큰을 시/군/구로 사용. Phase 2에서 법정동 코드 조회로 정밀화.
     */
    private fun parseSigunguFromAddress(address: String?): String? =
        address?.trim()?.split(" ")?.getOrNull(1)?.takeIf { it.isNotBlank() }
}
