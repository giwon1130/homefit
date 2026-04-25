package app.homefit.infrastructure.cache

import app.homefit.domain.listing.ListingDetail
import app.homefit.domain.listing.ListingPage
import app.homefit.domain.listing.ListingQuery
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 청약 목록/상세 Redis 캐시.
 *  - 목록(`search`)은 query 해시 키로 5분 TTL — 변하는 정렬/필터 다양하지만 같은 조합 반복 방문 흔함.
 *  - 상세(`findDetail`)는 30분 TTL — listing 이 자주 바뀌지 않음 (cron 6h 단위 ingestion).
 *
 * 이 정도 TTL 이면 ingestion 후 최대 5~30분 stale 허용. 운영상 충분.
 */
@Component
class ListingCache(
    private val redis: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    fun getList(query: ListingQuery): ListingPage? = read(listKey(query), ListingPage::class.java)
    fun putList(query: ListingQuery, page: ListingPage) = write(listKey(query), page, LIST_TTL)
    fun getDetail(id: Long): ListingDetail? = read(detailKey(id), ListingDetail::class.java)
    fun putDetail(id: Long, detail: ListingDetail) = write(detailKey(id), detail, DETAIL_TTL)

    private fun <T> read(key: String, type: Class<T>): T? {
        val raw = runCatching { redis.opsForValue().get(key) }
            .onFailure { log.warn("redis get failed: {}", it.message) }
            .getOrNull() ?: return null
        return runCatching { mapper.readValue(raw, type) }
            .onFailure { log.warn("listing cache deserialize failed for {}: {}", key, it.message) }
            .getOrNull()
    }

    private fun write(key: String, value: Any, ttl: Duration) {
        runCatching {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl)
        }.onFailure { log.warn("redis put failed: {}", it.message) }
    }

    private fun listKey(q: ListingQuery): String =
        "homefit:lst:v1:${q.sido.orEmpty()}:${q.sigungu.orEmpty()}:${q.types.joinToString(",")}:${q.activeOnly}:${q.sort}:${q.page}:${q.size}"

    private fun detailKey(id: Long): String = "homefit:lst:detail:v1:$id"

    companion object {
        private val LIST_TTL: Duration = Duration.ofMinutes(5)
        private val DETAIL_TTL: Duration = Duration.ofMinutes(30)
    }
}
