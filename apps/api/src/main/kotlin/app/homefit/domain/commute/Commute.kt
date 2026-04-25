package app.homefit.domain.commute

import java.math.BigDecimal
import java.time.OffsetDateTime

data class CommuteCacheRow(
    val originLat: BigDecimal,
    val originLng: BigDecimal,
    val destLat: BigDecimal,
    val destLng: BigDecimal,
    val totalMinutes: Int,
    val walkMinutes: Int?,
    val transfers: Int?,
    val paymentKrw: Int?,
    val cachedAt: OffsetDateTime,
)

interface CommuteCacheRepository {
    fun find(originLat: BigDecimal, originLng: BigDecimal, destLat: BigDecimal, destLng: BigDecimal, freshAfter: OffsetDateTime): CommuteCacheRow?
    fun save(row: CommuteCacheRow)
}
