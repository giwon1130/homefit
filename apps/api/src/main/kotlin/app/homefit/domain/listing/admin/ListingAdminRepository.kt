package app.homefit.domain.listing.admin

import java.math.BigDecimal

/**
 * 관리자 큐레이션용 — 개별 listing 의 수동 보강 수정.
 * Ingestion 이 채우지 못한 필드(주소/좌표/세대수) 보충용.
 */
interface ListingAdminRepository {
    fun updateCuratedFields(id: Long, patch: ListingPatch): Int
}

data class ListingPatch(
    val address: String? = null,
    val sido: String? = null,
    val sigungu: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val totalSupply: Int? = null,
    val name: String? = null,
)
