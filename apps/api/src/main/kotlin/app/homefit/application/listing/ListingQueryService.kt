package app.homefit.application.listing

import app.homefit.domain.listing.ListingDetail
import app.homefit.domain.listing.ListingPage
import app.homefit.domain.listing.ListingQuery
import app.homefit.domain.listing.ListingQueryRepository
import org.springframework.stereotype.Service

@Service
class ListingQueryService(private val repo: ListingQueryRepository) {
    fun search(query: ListingQuery): ListingPage = repo.search(query)
    fun findDetail(id: Long): ListingDetail? = repo.findDetail(id)
}
