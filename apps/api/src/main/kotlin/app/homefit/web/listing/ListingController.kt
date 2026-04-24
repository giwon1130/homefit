package app.homefit.web.listing

import app.homefit.application.listing.EligibilityService
import app.homefit.application.listing.ListingQueryService
import app.homefit.domain.listing.ListingQuery
import app.homefit.domain.listing.ListingType
import app.homefit.web.security.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/listings")
class ListingController(
    private val service: ListingQueryService,
    private val eligibility: EligibilityService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) sido: String?,
        @RequestParam(required = false) sigungu: String?,
        @RequestParam(required = false) type: List<ListingType>?,
        @RequestParam(defaultValue = "true") activeOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "CLOSING") sort: ListingQuery.Sort,
    ): ListingPageResponse {
        val q = ListingQuery(
            sido = sido?.takeIf { it.isNotBlank() },
            sigungu = sigungu?.takeIf { it.isNotBlank() },
            types = type.orEmpty(),
            activeOnly = activeOnly,
            page = page.coerceAtLeast(0),
            size = size.coerceIn(1, 100),
            sort = sort,
        )
        return ListingPageResponse.from(service.search(q))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): ListingDetailResponse {
        val d = service.findDetail(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return ListingDetailResponse.from(d)
    }

    @GetMapping("/{id}/eligibility")
    fun eligibility(@CurrentUserId userId: Long, @PathVariable id: Long): EligibilityResponse {
        val result = eligibility.evaluate(userId, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return EligibilityResponse.from(result)
    }
}
