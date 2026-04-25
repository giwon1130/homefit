package app.homefit.infrastructure.odsay

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

data class CommuteSummary(
    val totalMinutes: Int,
    val walkMinutes: Int?,
    val transfers: Int?,
    val paymentKrw: Int?,
)

/**
 * ODsay 대중교통 길찾기. searchPubTransPathT.
 *
 * 키는 Referer 기반 도메인 화이트리스트 — 등록된 URI 중 하나로 Referer 보내야 통과.
 */
@Component
class OdsayClient(
    @Value("\${homefit.odsay.api-key:}") private val apiKey: String,
    @Value("\${homefit.odsay.referer:https://homefit.app/}") private val referer: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl("https://api.odsay.com")
        .defaultHeader("Referer", referer)
        .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()

    fun findCommute(originLat: BigDecimal, originLng: BigDecimal, destLat: BigDecimal, destLng: BigDecimal): CommuteSummary? {
        if (apiKey.isBlank()) {
            log.warn("ODSAY_API_KEY not set — commute skipped")
            return null
        }
        val resp = runCatching {
            webClient.get()
                .uri {
                    it.path("/v1/api/searchPubTransPathT")
                        .queryParam("SX", originLng.toPlainString())
                        .queryParam("SY", originLat.toPlainString())
                        .queryParam("EX", destLng.toPlainString())
                        .queryParam("EY", destLat.toPlainString())
                        .queryParam("apiKey", apiKey)
                        .build()
                }
                .retrieve()
                .bodyToMono(OdsayResponse::class.java)
                .block()
        }.onFailure { log.warn("odsay call failed: {}", it.message) }
            .getOrNull() ?: return null

        if (resp.error != null) {
            log.warn("odsay error: {}", resp.error)
            return null
        }
        val first = resp.result?.path?.firstOrNull()?.info ?: return null
        return CommuteSummary(
            totalMinutes = first.totalTime ?: return null,
            walkMinutes = first.totalWalkTime,
            transfers = (first.busTransitCount ?: 0) + (first.subwayTransitCount ?: 0),
            paymentKrw = first.payment,
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OdsayResponse(
        val result: Result? = null,
        val error: List<Map<String, String>>? = null,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Result(val path: List<Path> = emptyList())

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Path(val info: Info? = null)

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Info(
            val totalTime: Int? = null,
            val totalWalkTime: Int? = null,
            val busTransitCount: Int? = null,
            val subwayTransitCount: Int? = null,
            val payment: Int? = null,
        )
    }
}
