package app.homefit.application.listing

import app.homefit.application.commute.CommuteService
import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingQuery
import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.domain.listing.matching.MatchingScore
import app.homefit.domain.listing.matching.MatchingScoreCalculator
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import app.homefit.domain.profile.ProfileRepository
import app.homefit.infrastructure.cache.MatchCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MatchedListing(val listing: Listing, val score: MatchingScore)

data class MatchedListingPage(
    val content: List<MatchedListing>,
    val page: Int,
    val size: Int,
    val total: Long,
)

/**
 * 사용자 프로필 기반 매칭 스코어 정렬.
 *
 * 흐름:
 *  1) Redis 캐시 hit → 캐시된 정렬 결과를 인메모리 페이징.
 *  2) miss → 활성 청약 모두 가져와 점수 계산/정렬 → 캐시 저장 (30분).
 *
 * 무효화는 ProfileService 측에서 MatchCache.evict(userId) 호출.
 */
@Service
class MatchingService(
    private val listings: ListingQueryRepository,
    private val profile: ProfileRepository,
    private val calculator: MatchingScoreCalculator,
    private val commute: CommuteService,
    private val cache: MatchCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun searchMatched(userId: Long, query: ListingQuery): MatchedListingPage {
        val scored = cache.get(userId) ?: computeAndCache(userId)
        val from = query.page * query.size
        val to = (from + query.size).coerceAtMost(scored.size)
        val pageContent = if (from < scored.size) scored.subList(from, to) else emptyList()
        return MatchedListingPage(pageContent, query.page, query.size, scored.size.toLong())
    }

    private fun computeAndCache(userId: Long): List<MatchedListing> {
        log.debug("computing match scores for user {}", userId)
        val all = listings.search(
            ListingQuery(activeOnly = true, page = 0, size = MAX_FETCH, sort = ListingQuery.Sort.MATCH),
        )
        val unitMap = listings.findUnitsByListingIds(all.content.map { it.id })

        val core = profile.findCore(userId) ?: ProfileCore()
        val members = profile.findHouseholdMembers(userId)
        val incomes = profile.findIncomes(userId)
        val history = profile.findHousingHistory(userId)
        val prefs = profile.findPreferences(userId) ?: Preferences()
        val workplaces = profile.findWorkplaces(userId)
        val assets = profile.findAssets(userId)   // v2: budget DSR 검증용

        val commuteLookup: (Pair<java.math.BigDecimal, java.math.BigDecimal>, Pair<java.math.BigDecimal, java.math.BigDecimal>) -> Int? =
            { origin, dest -> commute.get(origin.first, origin.second, dest.first, dest.second)?.totalMinutes }

        val scored = all.content.map { l ->
            MatchedListing(
                listing = l,
                score = calculator.calculate(
                    l, unitMap[l.id].orEmpty(), core, members, incomes, history, prefs, workplaces,
                    assets = assets,
                    commuteLookup = commuteLookup,
                ),
            )
        }.sortedByDescending { it.score.total }

        cache.put(userId, scored)
        return scored
    }

    companion object {
        private const val MAX_FETCH = 1000
    }
}
