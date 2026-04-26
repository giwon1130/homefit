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

    /**
     * Railway(SG 리전) → VWorld 호출이 502/connection-reset 으로 거의 다 실패.
     * 연속 실패가 임계치를 넘으면 이후 호출을 즉시 null 로 포기 (각 호출 마다 5~10초
     * 타임아웃 누적되는 걸 방지). 백필은 로컬 스크립트로 처리.
     */
    @Volatile private var consecutiveFailures = 0
    @Volatile private var disabledUntilEpochMs: Long = 0L

    override fun geocode(address: String): GeoPoint? {
        if (apiKey.isBlank()) {
            log.warn("VWORLD_API_KEY not set — geocoding skipped")
            return null
        }
        if (System.currentTimeMillis() < disabledUntilEpochMs) return null

        val cleaned = clean(address)
        // ROAD(도로명) 우선
        val result = queryOnce(cleaned, "ROAD") ?: queryOnce(cleaned, "PARCEL")
        if (result == null) {
            consecutiveFailures += 1
            if (consecutiveFailures >= MAX_CONSEC_FAILS) {
                disabledUntilEpochMs = System.currentTimeMillis() + COOLDOWN_MS
                log.warn("vworld disabled for {}ms after {} consecutive failures (use local backfill)", COOLDOWN_MS, consecutiveFailures)
                consecutiveFailures = 0
            }
        } else {
            consecutiveFailures = 0
        }
        return result
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

    companion object {
        private const val MAX_CONSEC_FAILS = 3
        private const val COOLDOWN_MS = 30 * 60_000L  // 30분
    }
}
