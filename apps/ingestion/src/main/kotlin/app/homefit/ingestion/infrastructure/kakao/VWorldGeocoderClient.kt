package app.homefit.ingestion.infrastructure.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

/**
 * VWorld(국토부) 지오코더 2.0 — 정부 운영, 무료 무한정.
 *
 * 도로명 우선, 실패 시 지번으로 폴백.
 * 응답: response.result.point.{x, y} (x=lng, y=lat, EPSG:4326)
 */
@Component
@Primary
class VWorldGeocoderClient(
    @Value("\${homefit.vworld.api-key:}") private val apiKey: String,
) : Geocoder {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl("https://api.vworld.kr")
        .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()

    override fun geocode(address: String): GeoPoint? {
        if (apiKey.isBlank()) {
            log.warn("VWORLD_API_KEY not set — geocoding skipped")
            return null
        }
        val cleaned = clean(address)
        // ROAD(도로명) 우선
        return queryOnce(cleaned, "ROAD") ?: queryOnce(cleaned, "PARCEL")
    }

    private fun queryOnce(address: String, type: String): GeoPoint? {
        val resp = runCatching {
            webClient.get()
                .uri {
                    it.path("/req/address")
                        .queryParam("service", "address")
                        .queryParam("request", "getCoord")
                        .queryParam("format", "json")
                        .queryParam("type", type)
                        .queryParam("address", address)
                        .queryParam("key", apiKey)
                        .build()
                }
                .retrieve()
                .bodyToMono(VWorldResponse::class.java)
                .block()
        }.onFailure { log.warn("vworld geocode failed for '{}' ({}): {}", address, type, it.message) }
            .getOrNull() ?: return null

        val point = resp.response?.result?.point ?: return null
        return runCatching {
            GeoPoint(
                latitude = BigDecimal(point.y),
                longitude = BigDecimal(point.x),
            )
        }.getOrNull()
    }

    /** "경기도 수원시 장안구 영화동 93-6번지 일원" 같은 꼬리 제거. */
    private fun clean(address: String): String =
        address.trim()
            .replace(Regex("\\s+일원$"), "")
            .replace(Regex("\\s+일대$"), "")
            .replace(Regex("\\s+외$"), "")
            .replace(Regex("\\([^)]*\\)"), "")  // 괄호 주석 제거
            .trim()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VWorldResponse(val response: Inner? = null) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Inner(val result: Result? = null, val status: String? = null)

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Result(val point: Point? = null)

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Point(val x: String = "0", val y: String = "0")
    }
}
