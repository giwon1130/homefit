package app.homefit.domain.listing

interface ListingQueryRepository {
    fun search(query: ListingQuery): ListingPage
    fun findDetail(id: Long): ListingDetail?
    fun findUnitsByListingIds(ids: Collection<Long>): Map<Long, List<ListingUnit>>
}
