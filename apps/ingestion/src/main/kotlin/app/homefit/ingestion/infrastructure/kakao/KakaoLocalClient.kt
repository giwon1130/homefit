package app.homefit.ingestion.infrastructure.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

data class GeoPoint(val latitude: BigDecimal, val longitude: BigDecimal)

/**
 * Kakao Local API 주소 검색. 비즈 앱 인증 후 카카오맵 활성화 필요.
 * 현재는 비활성 — VWorldGeocoderClient 가 기본 Geocoder.
 */
@Component
class KakaoLocalClient(
    @Value("\${homefit.kakao.rest-api-key:}") private val apiKey: String,
) : Geocoder {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl("https://dapi.kakao.com")
        .defaultHeader("Authorization", "KakaoAK $apiKey")
        .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()

    override fun geocode(address: String): GeoPoint? {
        if (apiKey.isBlank()) {
            log.warn("KAKAO_REST_API_KEY not set — geocoding skipped")
            return null
        }
        val resp = runCatching {
            webClient.get()
                .uri { it.path("/v2/local/search/address.json").queryParam("query", address).build() }
                .retrieve()
                .bodyToMono(KakaoGeocodeResponse::class.java)
                .block()
        }.onFailure { log.warn("kakao geocode failed for '{}': {}", address, it.message) }
            .getOrNull() ?: return null

        val first = resp.documents.firstOrNull() ?: return null
        return runCatching {
            GeoPoint(
                latitude = BigDecimal(first.y),
                longitude = BigDecimal(first.x),
            )
        }.getOrNull()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KakaoGeocodeResponse(
        val documents: List<Document> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Document(
        @JsonProperty("address_name") val addressName: String? = null,
        val x: String? = null,
        val y: String? = null,
    )
}
