package app.homefit.infrastructure.persistence

import app.homefit.domain.commute.CommuteCacheRepository
import app.homefit.domain.commute.CommuteCacheRow
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class JdbcCommuteCacheRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : CommuteCacheRepository {

    private val mapper = RowMapper { rs: ResultSet, _ ->
        CommuteCacheRow(
            originLat = rs.getBigDecimal("origin_lat"),
            originLng = rs.getBigDecimal("origin_lng"),
            destLat = rs.getBigDecimal("dest_lat"),
            destLng = rs.getBigDecimal("dest_lng"),
            totalMinutes = rs.getInt("total_minutes"),
            walkMinutes = rs.getObject("walk_minutes") as? Int,
            transfers = rs.getObject("transfers") as? Int,
            paymentKrw = rs.getObject("payment_krw") as? Int,
            cachedAt = rs.getObject("cached_at", OffsetDateTime::class.java),
        )
    }

    override fun find(
        originLat: BigDecimal, originLng: BigDecimal,
        destLat: BigDecimal, destLng: BigDecimal,
        freshAfter: OffsetDateTime,
    ): CommuteCacheRow? = jdbc.query(
        """
        SELECT origin_lat, origin_lng, dest_lat, dest_lng,
               total_minutes, walk_minutes, transfers, payment_krw, cached_at
        FROM commute_cache
        WHERE origin_lat = :ol AND origin_lng = :og
          AND dest_lat = :dl AND dest_lng = :dg
          AND cached_at > :fresh
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("ol", originLat).addValue("og", originLng)
            .addValue("dl", destLat).addValue("dg", destLng)
            .addValue("fresh", freshAfter),
        mapper,
    ).firstOrNull()

    override fun save(row: CommuteCacheRow) {
        jdbc.update(
            """
            INSERT INTO commute_cache (origin_lat, origin_lng, dest_lat, dest_lng,
                                        total_minutes, walk_minutes, transfers, payment_krw, cached_at)
            VALUES (:ol, :og, :dl, :dg, :total, :walk, :transfers, :payment, now())
            ON CONFLICT (origin_lat, origin_lng, dest_lat, dest_lng) DO UPDATE SET
                total_minutes = EXCLUDED.total_minutes,
                walk_minutes  = EXCLUDED.walk_minutes,
                transfers     = EXCLUDED.transfers,
                payment_krw   = EXCLUDED.payment_krw,
                cached_at     = now()
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("ol", row.originLat).addValue("og", row.originLng)
                .addValue("dl", row.destLat).addValue("dg", row.destLng)
                .addValue("total", row.totalMinutes)
                .addValue("walk", row.walkMinutes)
                .addValue("transfers", row.transfers)
                .addValue("payment", row.paymentKrw),
        )
    }
}
