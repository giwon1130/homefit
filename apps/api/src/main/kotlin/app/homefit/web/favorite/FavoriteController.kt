package app.homefit.web.favorite

import app.homefit.domain.favorite.FavoriteRepository
import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.web.listing.ListingSummaryResponse
import app.homefit.web.security.CurrentUserId
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/favorites")
class FavoriteController(
    private val favorites: FavoriteRepository,
    private val listings: ListingQueryRepository,
) {
    @GetMapping
    fun list(@CurrentUserId userId: Long): List<ListingSummaryResponse> {
        val ids = favorites.listIds(userId)
        if (ids.isEmpty()) return emptyList()
        // 단일 IN 쿼리로 한 번에 가져온 뒤 favorites 순서(생성 역순)대로 정렬 — N+1 제거
        val byId = listings.findByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }.map { ListingSummaryResponse.from(it) }
    }

    @PutMapping("/{listingId}")
    fun add(@CurrentUserId userId: Long, @PathVariable listingId: Long): Map<String, Boolean> {
        favorites.add(userId, listingId)
        return mapOf("favorited" to true)
    }

    @DeleteMapping("/{listingId}")
    fun remove(@CurrentUserId userId: Long, @PathVariable listingId: Long): Map<String, Boolean> {
        favorites.remove(userId, listingId)
        return mapOf("favorited" to false)
    }
}
