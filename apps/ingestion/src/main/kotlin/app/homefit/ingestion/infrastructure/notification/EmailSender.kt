package app.homefit.ingestion.infrastructure.notification

import app.homefit.ingestion.application.notification.PendingD1Notification
import app.homefit.ingestion.config.EmailNotificationProperties
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SMTP 메일 발송 어댑터. Spring Mail Starter (`JavaMailSender`) 를 그대로 활용.
 * 운영: SendGrid/Resend SMTP relay, 개발: maildev/mailhog.
 */
@Component
class EmailSender(
    private val mailSender: JavaMailSender,
    private val props: EmailNotificationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val krDate = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm").withZone(ZoneId.of("Asia/Seoul"))

    /**
     * 즐겨찾기한 청약의 D-1 안내 메일 발송.
     * 호출자(NotificationDispatchService) 가 발송 결과를 로그 테이블에 기록.
     *
     * @throws RuntimeException SMTP 실패시 — 호출자에서 캐치해서 FAILED 로그.
     */
    fun sendD1(target: PendingD1Notification) {
        if (props.from.isBlank()) {
            error("homefit.notification.email.from 미설정 — 발신자 주소가 필요합니다")
        }
        val mime = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mime, false, "UTF-8")
        helper.setFrom(props.from)
        helper.setTo(target.userEmail)
        helper.setSubject("[homefit] 내일 청약 마감: ${target.listingName}")
        helper.setText(buildHtml(target), true)
        mailSender.send(mime)
        log.info("d1 email sent to {} listing={}", target.userEmail, target.listingId)
    }

    private fun buildHtml(t: PendingD1Notification): String {
        val deadline = krDate.format(t.applicationEnd)
        val name = t.userDisplayName?.takeIf { it.isNotBlank() } ?: "homefit 사용자"
        val detailUrl = "${props.webBaseUrl.trimEnd('/')}/listings/${t.listingId}"
        val typeLabel = LISTING_TYPE_LABEL[t.listingType] ?: t.listingType
        return """
            <!doctype html>
            <html lang="ko"><body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; line-height: 1.6; color: #18181b; max-width: 560px; margin: 0 auto; padding: 24px;">
              <h2 style="margin: 0 0 16px;">내일 청약 마감 안내</h2>
              <p>안녕하세요 $name 님,<br>즐겨찾기에 담아두신 청약의 접수 마감이 <strong>내일</strong>입니다.</p>
              <div style="background: #fafafa; border: 1px solid #e4e4e7; border-radius: 12px; padding: 16px; margin: 16px 0;">
                <div style="font-size: 12px; color: #71717a;">$typeLabel</div>
                <div style="font-size: 18px; font-weight: 700; margin-top: 4px;">${escape(t.listingName)}</div>
                <div style="margin-top: 8px;">접수 마감: <strong>$deadline</strong></div>
              </div>
              <p style="margin: 24px 0;">
                <a href="$detailUrl"
                   style="background: #2563eb; color: #fff; padding: 10px 18px; border-radius: 8px; text-decoration: none; font-weight: 600;">
                  단지 상세 보기
                </a>
              </p>
              <hr style="border: none; border-top: 1px solid #e4e4e7; margin: 32px 0 16px;">
              <p style="font-size: 12px; color: #71717a;">
                이 메일은 homefit 즐겨찾기 알림입니다.<br>
                알림을 받지 않으려면 프로필에서 이메일 알림을 끄실 수 있어요.
              </p>
            </body></html>
        """.trimIndent()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private val LISTING_TYPE_LABEL = mapOf(
            "PRIVATE_SALE" to "민영분양",
            "PUBLIC_SALE" to "공공분양",
            "NEWLYWED_HOPE" to "신혼희망타운",
            "HAPPY_HOUSE" to "행복주택",
            "PURCHASE_RENTAL" to "매입임대",
            "JEONSE_RENTAL" to "전세임대",
            "NATIONAL_RENTAL" to "국민임대",
            "OTHER" to "기타",
        )
    }
}
