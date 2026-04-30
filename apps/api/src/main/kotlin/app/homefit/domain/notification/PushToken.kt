package app.homefit.domain.notification

import java.time.OffsetDateTime

enum class PushPlatform { IOS, ANDROID, EXPO }

data class PushToken(
    val id: Long,
    val userId: Long,
    val platform: PushPlatform,
    val token: String,
    val createdAt: OffsetDateTime,
    val lastSeenAt: OffsetDateTime,
)

interface PushTokenRepository {
    /**
     * 토큰 등록. 같은 토큰이 다른 유저에게 매핑돼있다면 새 유저로 옮김 (last write wins).
     * 같은 (user, token) 이면 last_seen_at 만 갱신.
     */
    fun upsert(userId: Long, platform: PushPlatform, token: String): PushToken

    /** 로그아웃 시 디바이스 토큰 제거. */
    fun deleteByToken(token: String): Int

    /** 사용자가 보유한 활성 토큰 목록 (디스패처용). */
    fun findActiveByUserId(userId: Long): List<PushToken>
}
