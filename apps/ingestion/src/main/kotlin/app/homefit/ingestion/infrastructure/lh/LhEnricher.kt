package app.homefit.ingestion.infrastructure.lh

import app.homefit.ingestion.domain.listing.RawListing
import app.homefit.ingestion.domain.listing.RawListingUnit
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * LH 목록(LhMapper 결과) 위에 detail + supply 응답을 덮어 풍부한 RawListing 으로.
 */
@Component
class LhEnricher(
    private val client: LhClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone = ZoneId.of("Asia/Seoul")
    private val plainDate = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val acpRangeRegex = Regex(
        """(\d{4})\.(\d{2})\.(\d{2})\s*(\d{2}):(\d{2})\s*~\s*(\d{4})\.(\d{2})\.(\d{2})\s*(\d{2}):(\d{2})"""
    )

    fun enrich(base: RawListing, notice: LhClient.LhNotice): RawListing {
        val splCd = notice.splInfTpCd ?: return base
        val sysCd = notice.ccrCnntSysDsCd ?: return base
        val uppCd = notice.uppAisTpCd ?: return base

        val detail = client.fetchDetail(notice.panId!!, splCd, sysCd, uppCd, notice.aisTpCd)
        val supply = client.fetchSupply(notice.panId, splCd, sysCd, uppCd)

        val sbd = detail?.firstDsRow("dsSbd")
        val scdl = detail?.firstDsRow("dsSplScdl")

        val name = sbd?.text("BZDT_NM") ?: base.name
        val baseAddr = sbd?.text("LCT_ARA_ADR")
        val detailAddr = sbd?.text("LCT_ARA_DTL_ADR")
        val fullAddr = listOfNotNull(baseAddr, detailAddr).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { null }
        val sigungu = parseSigungu(baseAddr)
        val totalSupply = sbd?.text("SUM_TOT_HSH_CNT")?.toIntOrNull()
        val moveIn = parseMoveIn(sbd?.text("MVIN_XPC_YM"))

        val winnerAnc = parsePlain(scdl?.text("PZWR_ANC_DT"))
        val ctrtSt = parsePlain(scdl?.text("CTRT_ST_DT"))
        val ctrtEd = parsePlain(scdl?.text("CTRT_ED_DT"))
        val (acpStart, acpEnd) = parseAcpDttm(scdl?.text("ACP_DTTM"))

        val units = supply?.let { extractUnits(it) }.orEmpty()

        return base.copy(
            name = name,
            address = fullAddr ?: base.address,
            sigungu = sigungu ?: base.sigungu,
            totalSupply = totalSupply ?: base.totalSupply,
            moveInDate = moveIn ?: base.moveInDate,
            winnerAnnouncementDate = winnerAnc ?: base.winnerAnnouncementDate,
            contractStartDate = ctrtSt ?: base.contractStartDate,
            contractEndDate = ctrtEd ?: base.contractEndDate,
            applicationStart = acpStart ?: base.applicationStart,
            applicationEnd = acpEnd ?: base.applicationEnd,
            units = units,
        )
    }

    /**
     * supply 응답에는 dsList01(분양), dsList02(임대), dsList03(분납), dsList04(다른 임대) 등이 섞여 옴.
     * 비어있지 않은 dsListNN 들을 모두 합쳐 RawListingUnit 으로 변환.
     */
    private fun extractUnits(supply: JsonNode): List<RawListingUnit> {
        val out = mutableListOf<RawListingUnit>()
        for (block in supply) {
            val obj = block as? com.fasterxml.jackson.databind.node.ObjectNode ?: continue
            val it = obj.fields()
            while (it.hasNext()) {
                val (key, arr) = it.next().let { it.key to it.value }
                if (!key.startsWith("dsList") || key.endsWith("Nm") || !arr.isArray) continue
                for (row in arr) {
                    val unitType = row.text("HTY_NM")
                    val sizeM2 = row.text("SPL_AR")?.toBigDecimalOrNull()
                        ?: row.text("RSDN_DDO_AR")?.toBigDecimalOrNull()
                    val supplyCount = row.text("SIL_HSH_CNT")?.toIntOrNull()
                    val price = row.text("SIL_AMT")?.toLongOrNull() ?: row.text("ELY_DSU_AMT")?.toLongOrNull()
                    val deposit = row.text("LS_GMY")?.toLongOrNull()
                    val monthly = row.text("MM_RFE")?.toIntOrNull()
                    if (unitType == null && sizeM2 == null && deposit == null && price == null) continue
                    out += RawListingUnit(
                        modelNo = null,
                        unitType = unitType,
                        sizeM2 = sizeM2,
                        supplyCount = supplyCount,
                        priceMaxKrw = price,
                        depositAmount = deposit,
                        monthlyRent = monthly,
                        rawJson = objectMapper.writeValueAsString(row),
                    )
                }
            }
        }
        return out
    }

    private fun JsonNode.firstDsRow(name: String): JsonNode? {
        if (!this.isArray) return null
        for (block in this) {
            val ds = block.get(name) ?: continue
            if (ds.isArray && ds.size() > 0) return ds[0]
        }
        return null
    }

    private fun JsonNode.text(field: String): String? =
        get(field)?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }

    private fun parsePlain(raw: String?): LocalDate? =
        raw?.takeIf { it.length == 8 }?.let { runCatching { LocalDate.parse(it, plainDate) }.getOrNull() }

    private fun parseMoveIn(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        // "2021년 10월" / "202110" / "2021.10" 다양한 포맷 가능
        val rx = Regex("""(\d{4})[^0-9]?(\d{1,2})""")
        val m = rx.find(raw) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt().coerceIn(1, 12)
        return runCatching { LocalDate.of(y, mo, 1) }.getOrNull()
    }

    private fun parseSigungu(addr: String?): String? =
        addr?.split(" ")?.let { tokens ->
            // 두 번째 토큰부터 "시/군/구" 로 끝나면 거기까지
            tokens.getOrNull(1)?.takeIf { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") }
        }

    private fun parseAcpDttm(raw: String?): Pair<OffsetDateTime?, OffsetDateTime?> {
        if (raw.isNullOrBlank()) return null to null
        val m = acpRangeRegex.find(raw) ?: return null to null
        val (y1, m1, d1, h1, mi1, y2, m2, d2, h2, mi2) = m.destructured
        return runCatching {
            val s = LocalDateTime.of(y1.toInt(), m1.toInt(), d1.toInt(), h1.toInt(), mi1.toInt())
                .atZone(zone).toOffsetDateTime()
            val e = LocalDateTime.of(y2.toInt(), m2.toInt(), d2.toInt(), h2.toInt(), mi2.toInt())
                .atZone(zone).toOffsetDateTime()
            s to e
        }.getOrElse { null to null }
    }
}
