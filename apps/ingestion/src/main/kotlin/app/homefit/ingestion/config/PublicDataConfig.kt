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

@ConfigurationProperties(prefix = "homefit.lh")
data class LhProperties(
    val apiKey: String = "",
    val lookbackDays: Long = 365,
    val pageSize: Int = 100,
)

/**
 * 알림 발송 설정. SMTP 자체 호스트/포트는 spring.mail.* 로 별도 관리.
 * - from: 발신자 주소 (예: "homefit <noreply@homefit.app>")
 * - webBaseUrl: 메일 본문 안의 단지 상세 링크 prefix (예: "https://homefit.app")
 * - enabled: false 면 스케줄러 자체를 끔 (SMTP 미설정 환경 안전장치)
 */
@ConfigurationProperties(prefix = "homefit.notification.email")
data class EmailNotificationProperties(
    val enabled: Boolean = false,
    val from: String = "",
    val webBaseUrl: String = "https://homefit.app",
)
