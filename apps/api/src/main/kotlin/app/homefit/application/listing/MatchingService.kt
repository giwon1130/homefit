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
 * 통근 점수는 CommuteService 통해 ODsay 캐시 조회 (없으면 외부 호출).
 *
 * 활성 청약 < 1000건 가정으로 인메모리 정렬. 후속 PR에서 matches 테이블 캐싱.
 */
@Service
class MatchingService(
    private val listings: ListingQueryRepository,
    private val profile: ProfileRepository,
    private val calculator: MatchingScoreCalculator,
    private val commute: CommuteService,
) {
    @Transactional(readOnly = true)
    fun searchMatched(userId: Long, query: ListingQuery): MatchedListingPage {
        val all = listings.search(query.copy(page = 0, size = MAX_FETCH))
        val unitMap = listings.findUnitsByListingIds(all.content.map { it.id })

        val core = profile.findCore(userId) ?: ProfileCore()
        val members = profile.findHouseholdMembers(userId)
        val incomes = profile.findIncomes(userId)
        val history = profile.findHousingHistory(userId)
        val prefs = profile.findPreferences(userId) ?: Preferences()
        val workplaces = profile.findWorkplaces(userId)

        val commuteLookup: (Pair<java.math.BigDecimal, java.math.BigDecimal>, Pair<java.math.BigDecimal, java.math.BigDecimal>) -> Int? =
            { origin, dest -> commute.get(origin.first, origin.second, dest.first, dest.second)?.totalMinutes }

        val scored = all.content.map { l ->
            MatchedListing(
                listing = l,
                score = calculator.calculate(l, unitMap[l.id].orEmpty(), core, members, incomes, history, prefs, workplaces, commuteLookup),
            )
        }.sortedByDescending { it.score.total }

        val from = query.page * query.size
        val to = (from + query.size).coerceAtMost(scored.size)
        val pageContent = if (from < scored.size) scored.subList(from, to) else emptyList()
        return MatchedListingPage(pageContent, query.page, query.size, scored.size.toLong())
    }

    companion object {
        private const val MAX_FETCH = 1000
    }
}
