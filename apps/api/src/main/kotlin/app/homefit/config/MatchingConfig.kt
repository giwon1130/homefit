package app.homefit.config

import app.homefit.domain.listing.eligibility.EligibilityEngine
import app.homefit.domain.listing.matching.MatchingScoreCalculator
import app.homefit.domain.loan.LoanCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MatchingConfig {
    @Bean
    fun matchingScoreCalculator(eligibility: EligibilityEngine) = MatchingScoreCalculator(eligibility)

    @Bean
    fun loanCalculator(props: LoanProperties) = LoanCalculator(props)
}
