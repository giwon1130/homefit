package app.homefit.application.listing

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
 * 사용자 프로필 기반으로 청약을 매칭 스코어 순 정렬.
 * v0: 자격 + 예산 + 지역 (통근은 후속 PR).
 *
 * 현재 동작 가정상 활성 청약 수가 수백 건 이하라 한 번에 가져와 인메모리 정렬.
 * 이후 데이터 늘면 매치 결과를 `matches` 테이블에 캐싱.
 */
@Service
class MatchingService(
    private val listings: ListingQueryRepository,
    private val profile: ProfileRepository,
    private val calculator: MatchingScoreCalculator,
) {
    @Transactional(readOnly = true)
    fun searchMatched(userId: Long, query: ListingQuery): MatchedListingPage {
        // Phase 2 v0: 한 번에 가져와 인메모리 정렬. 활성 청약 < 1000 건 가정.
        val all = listings.search(query.copy(page = 0, size = MAX_FETCH))
        val unitMap = listings.findUnitsByListingIds(all.content.map { it.id })

        val core = profile.findCore(userId) ?: ProfileCore()
        val members = profile.findHouseholdMembers(userId)
        val incomes = profile.findIncomes(userId)
        val history = profile.findHousingHistory(userId)
        val prefs = profile.findPreferences(userId) ?: Preferences()

        val scored = all.content.map { l ->
            MatchedListing(
                listing = l,
                score = calculator.calculate(l, unitMap[l.id].orEmpty(), core, members, incomes, history, prefs),
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
