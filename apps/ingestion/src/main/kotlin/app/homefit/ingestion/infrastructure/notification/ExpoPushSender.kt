package app.homefit.ingestion.infrastructure.notification

import app.homefit.ingestion.application.notification.PendingD1Notification
import app.homefit.ingestion.config.EmailNotificationProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Expo Push Service 어댑터. APNs/FCM 직접 호출 대신 Expo 가 추상화 — 무료, accessToken 불필요
 * (단 stream protection 위해 EXPO_ACCESS_TOKEN 환경 변수 권장).
 *
 * 단일 메시지 단위 호출 — 배치(최대 100개) 최적화는 추후. 지금은 i/o latency 무시 가능.
 *
 * webBaseUrl 은 메일과 공유 — push 의 deep link 도 결국 동일 listing 상세를 가리킴.
 */
@Component
class ExpoPushSender(
    private val emailProps: EmailNotificationProperties,
    @org.springframework.beans.factory.annotation.Value("\${homefit.notification.push.access-token:}")
    private val accessToken: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val krDate = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm").withZone(ZoneId.of("Asia/Seoul"))

    private val client: WebClient = WebClient.builder()
        .baseUrl("https://exp.host")
        .defaultHeader("accept", "application/json")
        .defaultHeader("accept-encoding", "gzip, deflate")
        .also { b ->
            if (accessToken.isNotBlank()) b.defaultHeader("authorization", "Bearer $accessToken")
        }
        .build()

    private val mapper = jacksonObjectMapper()

    /**
     * 단일 ExponentPushToken 으로 D-1 알림 발송.
     * @throws RuntimeException 토큰이 invalid 거나 Expo 가 실패 응답을 주면 — 호출자가 FAILED 로깅.
     */
    fun sendD1(token: String, target: PendingD1Notification) {
        val deadline = krDate.format(target.applicationEnd)
        val body = mapOf(
            "to" to token,
            "sound" to "default",
            "title" to "내일 청약 마감",
            "body" to "${target.listingName} · $deadline",
            "data" to mapOf(
                "kind" to "D_MINUS_1",
                "listingId" to target.listingId,
                "deepLink" to "${emailProps.webBaseUrl.trimEnd('/')}/listings/${target.listingId}",
            ),
            "priority" to "high",
        )
        val raw = try {
            client.post()
                .uri("/--/api/v2/push/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String::class.java)
                .block(Duration.ofSeconds(10))
                ?: error("expo push: empty response")
        } catch (e: WebClientResponseException) {
            error("expo push http ${e.statusCode}: ${e.responseBodyAsString.take(200)}")
        }

        // Expo 응답 형태: {"data": {"status": "ok"|"error", "message": "...", ...}}
        // 또는 {"data": [{"status": ...}]} (배치 시).
        val parsed = mapper.readTree(raw)
        val data = parsed["data"]
        val status: String? = when {
            data == null -> null
            data.isArray -> data.firstOrNull()?.get("status")?.asText()
            else -> data.get("status")?.asText()
        }
        if (status != "ok") {
            val message = when {
                data == null -> "no data"
                data.isArray -> data.firstOrNull()?.get("message")?.asText() ?: "unknown"
                else -> data.get("message")?.asText() ?: "unknown"
            }
            error("expo push not ok: $message")
        }
        log.info("expo push sent token=...{} listing={}", token.takeLast(8), target.listingId)
    }
}
