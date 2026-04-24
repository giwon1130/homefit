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

    fun fetchAptListings(page: Int, announcementFrom: LocalDate?): ApplyhomeAptListingsResponse =
        get("getAPTLttotPblancDetail", page, announcementFrom, ApplyhomeAptListingsResponse::class.java)

    fun fetchAptModels(page: Int, announcementFrom: LocalDate?): ApplyhomeAptModelsResponse =
        get("getAPTLttotPblancMdl", page, announcementFrom, ApplyhomeAptModelsResponse::class.java)

    private fun <T> get(operation: String, page: Int, announcementFrom: LocalDate?, type: Class<T>): T {
        val uriBuilder: (UriBuilder) -> URI = { b ->
            b.path(operation)
                .queryParam("page", page)
                .queryParam("perPage", pageSize)
                .queryParam("returnType", "JSON")
                .apply {
                    if (announcementFrom != null) {
                        queryParam("cond[RCRIT_PBLANC_DE::GTE]", announcementFrom.toString())
                    }
                }
                .build()
        }
        log.debug("publicdata GET {} page={} from={}", operation, page, announcementFrom)
        return webClient.get()
            .uri(uriBuilder)
            .retrieve()
            .bodyToMono(type)
            .block() ?: error("publicdata $operation returned null body")
    }
}
