package app.homefit.web.listing

import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingDetail
import app.homefit.domain.listing.ListingPage
import app.homefit.domain.listing.ListingType
import app.homefit.domain.listing.ListingUnit
import app.homefit.domain.listing.SupplyType
import app.homefit.domain.listing.eligibility.EligibilityDetail
import app.homefit.domain.listing.eligibility.EligibilityResult
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class ListingSummaryResponse(
    val id: Long,
    val name: String,
    val listingType: ListingType,
    val sido: String?,
    val sigungu: String?,
    val address: String?,
    val developer: String?,
    val applicationStart: OffsetDateTime?,
    val applicationEnd: OffsetDateTime?,
    val announcementDate: LocalDate?,
    val moveInDate: LocalDate?,
    val totalSupply: Int?,
    val documentUrl: String?,
) {
    companion object {
        fun from(l: Listing) = ListingSummaryResponse(
            id = l.id, name = l.name, listingType = l.listingType,
            sido = l.sido, sigungu = l.sigungu, address = l.address, developer = l.developer,
            applicationStart = l.applicationStart, applicationEnd = l.applicationEnd,
            announcementDate = l.announcementDate, moveInDate = l.moveInDate,
            totalSupply = l.totalSupply, documentUrl = l.rawDocumentUrl,
        )
    }
}

data class ListingPageResponse(
    val content: List<ListingSummaryResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
) {
    companion object {
        fun from(p: ListingPage) = ListingPageResponse(
            content = p.content.map { ListingSummaryResponse.from(it) },
            page = p.page, size = p.size, total = p.total,
        )
    }
}

data class UnitResponse(
    val id: Long,
    val modelNo: String?,
    val unitType: String?,
    val sizeM2: BigDecimal?,
    val supplyCount: Int?,
    val priceMaxKrw: Long?,
) {
    companion object {
        fun from(u: ListingUnit) = UnitResponse(u.id, u.modelNo, u.unitType, u.sizeM2, u.supplyCount, u.priceMaxKrw)
    }
}

data class ListingDetailResponse(
    val id: Long,
    val name: String,
    val listingType: ListingType,
    val sido: String?,
    val sigungu: String?,
    val address: String?,
    val developer: String?,
    val applicationStart: OffsetDateTime?,
    val applicationEnd: OffsetDateTime?,
    val announcementDate: LocalDate?,
    val winnerAnnouncementDate: LocalDate?,
    val contractStartDate: LocalDate?,
    val contractEndDate: LocalDate?,
    val moveInDate: LocalDate?,
    val totalSupply: Int?,
    val documentUrl: String?,
    val units: List<UnitResponse>,
) {
    companion object {
        fun from(d: ListingDetail) = ListingDetailResponse(
            id = d.listing.id, name = d.listing.name, listingType = d.listing.listingType,
            sido = d.listing.sido, sigungu = d.listing.sigungu, address = d.listing.address,
            developer = d.listing.developer,
            applicationStart = d.listing.applicationStart, applicationEnd = d.listing.applicationEnd,
            announcementDate = d.listing.announcementDate,
            winnerAnnouncementDate = d.listing.winnerAnnouncementDate,
            contractStartDate = d.listing.contractStartDate, contractEndDate = d.listing.contractEndDate,
            moveInDate = d.listing.moveInDate, totalSupply = d.listing.totalSupply,
            documentUrl = d.listing.rawDocumentUrl,
            units = d.units.map { UnitResponse.from(it) },
        )
    }
}

data class EligibilityDetailResponse(
    val supplyType: SupplyType,
    val eligible: Boolean,
    val reasons: List<String>,
) {
    companion object {
        fun from(d: EligibilityDetail) = EligibilityDetailResponse(d.supplyType, d.eligible, d.reasons)
    }
}

data class EligibilityResponse(
    val eligibleSupplyTypes: List<SupplyType>,
    val bestSupplyType: SupplyType?,
    val details: List<EligibilityDetailResponse>,
) {
    companion object {
        fun from(r: EligibilityResult) = EligibilityResponse(
            eligibleSupplyTypes = r.eligibleSupplyTypes,
            bestSupplyType = r.bestSupplyType,
            details = r.details.map { EligibilityDetailResponse.from(it) },
        )
    }
}
