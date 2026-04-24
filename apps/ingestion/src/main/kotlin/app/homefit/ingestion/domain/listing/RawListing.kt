package app.homefit.ingestion.domain.listing

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 외부 소스에서 정규화된 분양 공고 (DB로 upsert 되기 직전 단계).
 */
data class RawListing(
    val source: ListingSource,
    val sourceRef: String,
    val listingType: ListingType,
    val name: String,
    val developer: String?,
    val sido: String?,
    val sigungu: String?,
    val address: String?,
    val applicationStart: OffsetDateTime?,
    val applicationEnd: OffsetDateTime?,
    val announcementDate: LocalDate?,
    val winnerAnnouncementDate: LocalDate?,
    val contractStartDate: LocalDate?,
    val contractEndDate: LocalDate?,
    val moveInDate: LocalDate?,
    val totalSupply: Int?,
    val documentUrl: String?,
    val rawJson: String,
    val units: List<RawListingUnit>,
)

data class RawListingUnit(
    val modelNo: String?,
    val unitType: String?,
    val sizeM2: java.math.BigDecimal?,
    val supplyCount: Int?,
    val priceMaxKrw: Long?,
    val rawJson: String,
)
