package app.homefit.ingestion.web

import app.homefit.ingestion.application.listing.ListingIngestionService
import app.homefit.ingestion.application.notification.NotificationDispatchService
import app.homefit.ingestion.config.IngestionProperties
import app.homefit.ingestion.infrastructure.persistence.ListingRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

/**
 * 내부 관리용 엔드포인트. X-Admin-Token 헤더로 보호.
 * Railway 서비스는 기본 public 비활성이므로, 도메인 노출 안 하면 외부 접근 불가하지만
 * 방어적으로 토큰 체크를 둔다.
 */
@RestController
@RequestMapping("/admin/ingestion")
class IngestionAdminController(
    private val service: ListingIngestionService,
    private val props: IngestionProperties,
    private val listings: ListingRepository,
    private val notifications: NotificationDispatchService,
) {
    data class CoordEntry(val id: Long, val latitude: BigDecimal, val longitude: BigDecimal)
    data class BulkCoordRequest(val items: List<CoordEntry>)
    data class PolygonEntry(val id: Long, val geojson: Map<String, Any>)
    data class BulkPolygonRequest(val items: List<PolygonEntry>)
    @PostMapping("/run")
    fun runApt(@RequestHeader("X-Admin-Token") token: String): Map<String, Any> {
        require(token)
        val result = service.syncApt()
        return mapOf("pages" to result.pages, "upserted" to result.upserted)
    }

    @PostMapping("/run-lh")
    fun runLh(@RequestHeader("X-Admin-Token") token: String): Map<String, Any> {
        require(token)
        val result = service.syncLh()
        return mapOf("pages" to result.pages, "upserted" to result.upserted)
    }

    @PostMapping("/geocode-backfill")
    fun geocodeBackfill(
        @RequestHeader("X-Admin-Token") token: String,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") limit: Int,
    ): Map<String, Any> {
        require(token)
        val result = service.backfillCoordinates(limit.coerceIn(1, 500))
        return mapOf("attempted" to result.attempted, "succeeded" to result.succeeded)
    }

    /**
     * 외부에서 미리 지오코딩한 좌표 일괄 업데이트. Railway → VWorld 연결이 막혀있어
     * 로컬에서 지오코딩 후 푸시하는 우회 경로용.
     */
    @PostMapping("/coordinates")
    fun bulkUpdateCoordinates(
        @RequestHeader("X-Admin-Token") token: String,
        @RequestBody body: BulkCoordRequest,
    ): Map<String, Any> {
        require(token)
        var updated = 0
        for (entry in body.items) {
            val n = listings.updateCoordinates(entry.id, entry.latitude, entry.longitude)
            updated += n
        }
        return mapOf("received" to body.items.size, "updated" to updated)
    }

    /** 외부에서 미리 가져온 폴리곤 GeoJSON 일괄 업데이트 (Railway → VWorld Data API 막힘 우회). */
    @PostMapping("/polygons")
    fun bulkUpdatePolygons(
        @RequestHeader("X-Admin-Token") token: String,
        @RequestBody body: BulkPolygonRequest,
    ): Map<String, Any> {
        require(token)
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        var updated = 0
        for (entry in body.items) {
            val json = mapper.writeValueAsString(entry.geojson)
            updated += listings.updatePolygon(entry.id, json)
        }
        return mapOf("received" to body.items.size, "updated" to updated)
    }

    /** D-1 알림 수동 디스패치 (테스트/긴급 발송용). */
    @PostMapping("/dispatch-d1")
    fun dispatchD1(@RequestHeader("X-Admin-Token") token: String): Map<String, Any> {
        require(token)
        val r = notifications.dispatchD1()
        return mapOf(
            "candidates" to r.candidates,
            "sent" to r.sent,
            "failed" to r.failed,
            "skipped" to r.skipped,
        )
    }

    private fun require(token: String) {
        if (token != props.adminToken) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}
