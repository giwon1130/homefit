package app.homefit.domain.listing

interface ListingQueryRepository {
    fun search(query: ListingQuery): ListingPage
    fun findDetail(id: Long): ListingDetail?
}
