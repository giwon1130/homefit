package app.homefit.application.commute

import app.homefit.domain.commute.CommuteCacheRepository
import app.homefit.domain.commute.CommuteCacheRow
import app.homefit.infrastructure.odsay.OdsayClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

data class Commute(val totalMinutes: Int, val walkMinutes: Int?, val transfers: Int?)

@Service
class CommuteService(
    private val cache: CommuteCacheRepository,
    private val odsay: OdsayClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(originLat: BigDecimal, originLng: BigDecimal, destLat: BigDecimal, destLng: BigDecimal): Commute? {
        val ol = quantize(originLat, 4); val og = quantize(originLng, 4)
        val dl = quantize(destLat, 4); val dg = quantize(destLng, 4)

        val freshAfter = OffsetDateTime.now().minusDays(30)
        cache.find(ol, og, dl, dg, freshAfter)?.let {
            return Commute(it.totalMinutes, it.walkMinutes, it.transfers)
        }

        val summary = odsay.findCommute(ol, og, dl, dg) ?: run {
            log.debug("commute miss + odsay fail: ({},{}) -> ({},{})", ol, og, dl, dg)
            return null
        }
        cache.save(
            CommuteCacheRow(
                originLat = ol, originLng = og, destLat = dl, destLng = dg,
                totalMinutes = summary.totalMinutes,
                walkMinutes = summary.walkMinutes,
                transfers = summary.transfers,
                paymentKrw = summary.paymentKrw,
                cachedAt = OffsetDateTime.now(),
            ),
        )
        return Commute(summary.totalMinutes, summary.walkMinutes, summary.transfers)
    }

    private fun quantize(v: BigDecimal, scale: Int): BigDecimal = v.setScale(scale, RoundingMode.HALF_UP)
}
