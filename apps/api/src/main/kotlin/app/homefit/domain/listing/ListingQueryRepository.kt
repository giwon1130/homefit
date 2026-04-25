package app.homefit.domain.listing

interface ListingQueryRepository {
    fun search(query: ListingQuery): ListingPage
    fun findDetail(id: Long): ListingDetail?
    fun findByIds(ids: Collection<Long>): List<Listing>
    fun findUnitsByListingIds(ids: Collection<Long>): Map<Long, List<ListingUnit>>
}
