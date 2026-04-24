package app.homefit.config

import app.homefit.domain.listing.eligibility.EligibilityEngine
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EligibilityProperties::class)
class EligibilityConfig {
    @Bean
    fun eligibilityEngine(props: EligibilityProperties) = EligibilityEngine(props)
}
