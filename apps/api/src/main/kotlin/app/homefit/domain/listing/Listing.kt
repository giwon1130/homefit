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
) {
    enum class Sort { CLOSING, ANNOUNCEMENT, MOVE_IN, MATCH }
}
