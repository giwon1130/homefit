package app.homefit.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AuthProperties::class, LoanProperties::class)
class PropertiesConfig
