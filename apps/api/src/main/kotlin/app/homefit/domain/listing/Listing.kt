package app.homefit.domain.listing

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class Listing(
    val id: Long,
    val source: String,
    val sourceRef: String,
    val listingType: ListingType,
    val name: String,
    val developer: String?,
    val sido: String?,
    val sigungu: String?,
    val address: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    /** VWorld 연속지적도 GeoJSON FeatureCollection. raw JSON string. */
    val polygonGeoJson: String? = null,
    val applicationStart: OffsetDateTime?,
    val applicationEnd: OffsetDateTime?,
    val announcementDate: LocalDate?,
    val winnerAnnouncementDate: LocalDate?,
    val contractStartDate: LocalDate?,
    val contractEndDate: LocalDate?,
    val moveInDate: LocalDate?,
    val totalSupply: Int?,
    val rawDocumentUrl: String?,
)

data class ListingUnit(
    val id: Long,
    val listingId: Long,
    val modelNo: String?,
    val unitType: String?,
    val sizeM2: BigDecimal?,
    val supplyCount: Int?,
    val priceMaxKrw: Long?,
    val depositAmount: Long? = null,
    val monthlyRent: Int? = null,
)

data class ListingDetail(
    val listing: Listing,
    val units: List<ListingUnit>,
)

data class ListingPage(
    val content: List<Listing>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class ListingQuery(
    val sido: String? = null,
    val sigungu: String? = null,
    val types: List<ListingType> = emptyList(),
    val activeOnly: Boolean = true,   // 접수 기간 내 or 임박만
    val page: Int = 0,
    val size: Int = 20,
    val sort: Sort = Sort.CLOSING,
    /** 단지명/주소 부분 일치 (대소문자 무시). null/빈문자열 → 미적용. */
    val q: String? = null,
    /** 최저 분양가 한도 — 어떤 unit 의 priceMaxKrw 가 이 값 이하면 매치. */
    val maxPriceKrw: Long? = null,
    /** 최저 면적 — 어떤 unit 의 sizeM2 가 이 값 이상이면 매치. */
    val minSizeM2: java.math.BigDecimal? = null,
    /** 최대 면적 — 어떤 unit 의 sizeM2 가 이 값 이하면 매치. */
    val maxSizeM2: java.math.BigDecimal? = null,
) {
    enum class Sort { CLOSING, ANNOUNCEMENT, MOVE_IN, MATCH, PRICE_LOW, PRICE_HIGH }
}
