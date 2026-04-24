package app.homefit.ingestion.infrastructure.publicdata

import app.homefit.ingestion.config.PublicDataProperties
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptListingsResponse
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptModelsResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import java.net.URI
import java.time.LocalDate

/**
 * 한국부동산원 청약홈 분양정보 조회 서비스 (공공데이터포털 #15098547).
 *
 * 실제 REST 엔드포인트는 odcloud.kr. `Authorization: Infuser <apiKey>` 헤더 방식 사용.
 */
@Component
class PublicDataClient(
    props: PublicDataProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pageSize = props.pageSize

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(props.baseUrl)
        .defaultHeader("Authorization", "Infuser ${props.apiKey}")
        .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
        .build()

    fun fetchAptListings(page: Int, announcementFrom: LocalDate?): ApplyhomeAptListingsResponse {
        val cond = announcementFrom?.let { mapOf("RCRIT_PBLANC_DE::GTE" to it.toString()) } ?: emptyMap()
        return get("getAPTLttotPblancDetail", page, cond, ApplyhomeAptListingsResponse::class.java)
    }

    /**
     * Mdl 엔드포인트는 공고 단위 필드(RCRIT_PBLANC_DE)가 없으므로 날짜 필터를 받지 않음.
     * houseManageNo 가 주어지면 해당 공고의 주택형만 가져옴 (정상 케이스).
     * null이면 전체 페이지 순회 — 백필/복구용.
     */
    fun fetchAptModels(page: Int, houseManageNo: String? = null): ApplyhomeAptModelsResponse {
        val cond = houseManageNo?.let { mapOf("HOUSE_MANAGE_NO::EQ" to it) } ?: emptyMap()
        return get("getAPTLttotPblancMdl", page, cond, ApplyhomeAptModelsResponse::class.java)
    }

    private fun <T> get(operation: String, page: Int, cond: Map<String, String>, type: Class<T>): T {
        val uriBuilder: (UriBuilder) -> URI = { b ->
            b.path(operation)
                .queryParam("page", page)
                .queryParam("perPage", pageSize)
                .queryParam("returnType", "JSON")
                .apply {
                    cond.forEach { (k, v) -> queryParam("cond[$k]", v) }
                }
                .build()
        }
        log.debug("publicdata GET {} page={} cond={}", operation, page, cond)
        return webClient.get()
            .uri(uriBuilder)
            .retrieve()
            .bodyToMono(type)
            .block() ?: error("publicdata $operation returned null body")
    }
}
