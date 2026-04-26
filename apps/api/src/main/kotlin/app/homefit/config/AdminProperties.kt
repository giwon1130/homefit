package app.homefit.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 관리자 큐레이션 엔드포인트 보호용 토큰. ingestion 토큰 재사용 가능.
@ConfigurationProperties(prefix = "homefit.admin")
data class AdminProperties(
    val token: String,
)
