package app.homefit.infrastructure.cache

import app.homefit.application.listing.MatchedListing
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 사용자별 매칭 결과(스코어 정렬된 전체 리스트)를 Redis에 캐싱.
 * 정렬은 비싸지만 페이지네이션은 인메모리에서 cheap → 한 번 계산 후 30분간 재사용.
 *
 * 무효화 트리거: 프로필 변경 (가족/소득/직장/선호/주택이력 등 어느 거든).
 */
@Component
class MatchCache(
    private val redis: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val typeRef = object : TypeReference<List<MatchedListing>>() {}

    fun get(userId: Long): List<MatchedListing>? {
        val raw = runCatching { redis.opsForValue().get(key(userId)) }
            .onFailure { log.warn("redis get failed: {}", it.message) }
            .getOrNull() ?: return null
        return runCatching { mapper.readValue(raw, typeRef) }
            .onFailure { log.warn("match cache deserialize failed for user {}: {}", userId, it.message) }
            .getOrNull()
    }

    fun put(userId: Long, scored: List<MatchedListing>) {
        runCatching {
            val json = mapper.writeValueAsString(scored)
            redis.opsForValue().set(key(userId), json, TTL)
        }.onFailure { log.warn("redis put failed: {}", it.message) }
    }

    fun evict(userId: Long) {
        runCatching { redis.delete(key(userId)) }
            .onFailure { log.warn("redis evict failed: {}", it.message) }
    }

    // v2: 통근 점수 데이터 부족 페널티 적용 (이전 v1 결과 무효화)
    private fun key(userId: Long) = "homefit:match:v2:$userId"

    companion object {
        private val TTL: Duration = Duration.ofMinutes(30)
    }
}
