package app.homefit.domain.favorite

interface FavoriteRepository {
    fun add(userId: Long, listingId: Long)
    fun remove(userId: Long, listingId: Long)
    fun listIds(userId: Long): List<Long>
    fun exists(userId: Long, listingId: Long): Boolean
}
