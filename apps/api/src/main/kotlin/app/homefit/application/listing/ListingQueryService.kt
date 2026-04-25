package app.homefit.application.listing

import app.homefit.domain.listing.ListingDetail
import app.homefit.domain.listing.ListingPage
import app.homefit.domain.listing.ListingQuery
import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.infrastructure.cache.ListingCache
import org.springframework.stereotype.Service

@Service
class ListingQueryService(
    private val repo: ListingQueryRepository,
    private val cache: ListingCache,
) {
    fun search(query: ListingQuery): ListingPage =
        cache.getList(query) ?: repo.search(query).also { cache.putList(query, it) }

    fun findDetail(id: Long): ListingDetail? =
        cache.getDetail(id) ?: repo.findDetail(id)?.also { cache.putDetail(id, it) }
}
