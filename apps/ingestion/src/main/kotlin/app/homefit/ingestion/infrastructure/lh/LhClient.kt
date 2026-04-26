package app.homefit.ingestion.infrastructure.lh

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * LH 분양임대공고문 조회 (공공데이터포털 #15058530).
 *
 * 응답은 배열 — 각 요소가 객체(`dsSch`, `dsList`, `resHeader`).
 * - dsList: 공고 행 (PAN_ID, PAN_NM, AIS_TP_CD_NM, CNP_CD_NM, PAN_DT 등)
 * - dsSch: 검색 조건 echo
 * - resHeader: 메타
 */
@Component
class LhClient(
    @Value("\${homefit.lh.api-key:}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper = jacksonObjectMapper()

    private val webClient: WebClient = WebClient.builder()
        .baseUrl("http://apis.data.go.kr/B552555")
        .codecs { it.defaultCodecs().maxInMemorySize(8 * 1024 * 1024) }
        .build()

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun fetchNotices(page: Int, pageSize: Int = 100, from: LocalDate, to: LocalDate): List<LhNotice> {
        if (apiKey.isBlank()) {
            log.warn("LH_API_KEY not set — skip")
            return emptyList()
        }

        val builder: (UriBuilder) -> URI = { b ->
            b.path("/lhLeaseNoticeInfo1/lhLeaseNoticeInfo1")
                .queryParam("ServiceKey", apiKey)
                .queryParam("PG_SZ", pageSize)
                .queryParam("PAGE", page)
                .queryParam("PAN_NT_ST_DT", from.format(dateFmt))
                .queryParam("CLSG_DT", to.format(dateFmt))
                .build()
        }

        val raw = runCatching {
            webClient.get().uri(builder).retrieve().bodyToMono(String::class.java).block()
        }.onFailure { log.warn("LH fetch failed: {}", it.message) }
            .getOrNull() ?: return emptyList()

        return parse(raw)
    }

    private fun parse(raw: String): List<LhNotice> {
        val root: JsonNode = runCatching { mapper.readTree(raw) }.getOrNull() ?: return emptyList()
        if (!root.isArray) return emptyList()
        for (block in root) {
            val ds = block.get("dsList") ?: continue
            if (!ds.isArray) continue
            return ds.mapNotNull { node ->
                runCatching { mapper.treeToValue(node, LhNotice::class.java) }.getOrNull()
            }
        }
        return emptyList()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LhNotice(
        @com.fasterxml.jackson.annotation.JsonProperty("PAN_ID") val panId: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("PAN_NM") val panName: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("AIS_TP_CD_NM") val houseTypeName: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("UPP_AIS_TP_NM") val upperTypeName: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("CNP_CD_NM") val sido: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("PAN_DT") val noticeDate: String? = null,           // yyyymmdd
        @com.fasterxml.jackson.annotation.JsonProperty("PAN_NT_ST_DT") val noticeStart: String? = null,   // yyyy.mm.dd
        @com.fasterxml.jackson.annotation.JsonProperty("CLSG_DT") val closingDate: String? = null,        // yyyy.mm.dd
        @com.fasterxml.jackson.annotation.JsonProperty("PAN_SS") val status: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("DTL_URL") val detailUrl: String? = null,
    )
}
