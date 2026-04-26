package app.homefit.ingestion.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    PublicDataProperties::class,
    IngestionProperties::class,
    LhProperties::class,
)
class PropertiesConfig
