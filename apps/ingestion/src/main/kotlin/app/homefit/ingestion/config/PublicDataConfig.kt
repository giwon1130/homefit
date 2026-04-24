package app.homefit.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "homefit.publicdata")
data class PublicDataProperties(
    val baseUrl: String = "https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1/",
    val apiKey: String,
    val pageSize: Int = 500,
    val lookbackDays: Long = 90,
)

@ConfigurationProperties(prefix = "homefit.ingestion")
data class IngestionProperties(
    val adminToken: String,
)
