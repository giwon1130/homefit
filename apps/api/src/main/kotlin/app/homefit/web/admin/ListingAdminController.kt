package app.homefit.web.admin

import app.homefit.config.AdminProperties
import app.homefit.domain.listing.admin.ListingAdminRepository
import app.homefit.domain.listing.admin.ListingPatch
import app.homefit.infrastructure.cache.MatchCache
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/admin")
class ListingAdminController(
    private val repo: ListingAdminRepository,
    private val props: AdminProperties,
    private val matchCache: MatchCache,
) {
    @PatchMapping("/listings/{id}")
    fun patch(
        @PathVariable id: Long,
        @RequestHeader("X-Admin-Token") token: String,
        @Valid @RequestBody body: ListingPatchRequest,
    ): Map<String, Any> {
        if (token != props.token) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val updated = repo.updateCuratedFields(id, body.toDomain())
        if (updated == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "listing not found")
        // 매칭 캐시 무효화 (전체 사용자) — 단순화: 영향 받는 키만 알기 어려우므로 비워둠.
        // matchCache.invalidateAll()  ← 메서드 없음 → 그대로 두고 30분 TTL 으로 자연 갱신.
        return mapOf("updated" to updated)
    }
}

data class ListingPatchRequest(
    val name: String? = null,
    val address: String? = null,
    val sido: String? = null,
    val sigungu: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val totalSupply: Int? = null,
) {
    fun toDomain() = ListingPatch(
        name = name?.takeIf { it.isNotBlank() },
        address = address?.takeIf { it.isNotBlank() },
        sido = sido?.takeIf { it.isNotBlank() },
        sigungu = sigungu?.takeIf { it.isNotBlank() },
        latitude = latitude,
        longitude = longitude,
        totalSupply = totalSupply,
    )
}
