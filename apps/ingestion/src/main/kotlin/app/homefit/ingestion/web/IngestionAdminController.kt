package app.homefit.ingestion.web

import app.homefit.ingestion.application.listing.ListingIngestionService
import app.homefit.ingestion.config.IngestionProperties
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

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
) {
    @PostMapping("/run")
    fun runApt(@RequestHeader("X-Admin-Token") token: String): Map<String, Any> {
        require(token)
        val result = service.syncApt()
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

    private fun require(token: String) {
        if (token != props.adminToken) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}
