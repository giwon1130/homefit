package app.homefit.infrastructure.persistence

import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingDetail
import app.homefit.domain.listing.ListingPage
import app.homefit.domain.listing.ListingQuery
import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.domain.listing.ListingType
import app.homefit.domain.listing.ListingUnit
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class JdbcListingQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : ListingQueryRepository {

    private val listingMapper = RowMapper { rs: ResultSet, _ ->
        Listing(
            id = rs.getLong("id"),
            source = rs.getString("source"),
            sourceRef = rs.getString("source_ref"),
            listingType = ListingType.parse(rs.getString("listing_type")),
            name = rs.getString("name"),
            developer = rs.getString("developer"),
            sido = rs.getString("sido"),
            sigungu = rs.getString("sigungu"),
            address = rs.getString("address"),
            latitude = rs.getBigDecimal("latitude"),
            longitude = rs.getBigDecimal("longitude"),
            polygonGeoJson = rs.getString("polygon_geojson"),
            applicationStart = rs.getObject("application_start", OffsetDateTime::class.java),
            applicationEnd = rs.getObject("application_end", OffsetDateTime::class.java),
            announcementDate = rs.getDate("announcement_date")?.toLocalDate(),
            winnerAnnouncementDate = rs.getDate("winner_announcement_date")?.toLocalDate(),
            contractStartDate = rs.getDate("contract_start_date")?.toLocalDate(),
            contractEndDate = rs.getDate("contract_end_date")?.toLocalDate(),
            moveInDate = rs.getDate("move_in_date")?.toLocalDate(),
            totalSupply = rs.getObject("total_supply") as? Int,
            rawDocumentUrl = rs.getString("raw_document_url"),
        )
    }

    private val unitMapper = RowMapper { rs: ResultSet, _ ->
        ListingUnit(
            id = rs.getLong("id"),
            listingId = rs.getLong("listing_id"),
            modelNo = rs.getString("model_no"),
            unitType = rs.getString("unit_type"),
            sizeM2 = rs.getBigDecimal("size_m2"),
            supplyCount = rs.getObject("supply_count") as? Int,
            priceMaxKrw = rs.getObject("price_max_krw") as? Long,
            depositAmount = rs.getObject("deposit_amount") as? Long,
            monthlyRent = rs.getObject("monthly_rent") as? Int,
        )
    }

    override fun search(query: ListingQuery): ListingPage {
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource()

        query.sido?.let { where += "l.sido = :sido"; params.addValue("sido", it) }
        query.sigungu?.let { where += "l.sigungu = :sigungu"; params.addValue("sigungu", it) }
        if (query.types.isNotEmpty()) {
            where += "l.listing_type IN (:types)"
            params.addValue("types", query.types.map { it.name })
        }
        if (query.activeOnly) {
            // 접수 마감이 아직 안 끝났거나, 공고일이 최근 60일 이내인 것
            where += "(l.application_end IS NULL OR l.application_end > now() OR l.announcement_date > current_date - INTERVAL '60 days')"
        }
        query.q?.takeIf { it.isNotBlank() }?.let {
            // 단지명/주소 부분일치 (대소문자 무시).
            where += "(l.name ILIKE :qPattern OR l.address ILIKE :qPattern)"
            params.addValue("qPattern", "%${it.trim()}%")
        }
        query.maxPriceKrw?.let {
            // 어떤 unit 이라도 이 가격 이하면 매치.
            where += "EXISTS (SELECT 1 FROM listing_units u WHERE u.listing_id = l.id AND u.price_max_krw IS NOT NULL AND u.price_max_krw <= :maxPrice)"
            params.addValue("maxPrice", it)
        }
        query.minSizeM2?.let {
            where += "EXISTS (SELECT 1 FROM listing_units u WHERE u.listing_id = l.id AND u.size_m2 IS NOT NULL AND u.size_m2 >= :minSize)"
            params.addValue("minSize", it)
        }
        query.maxSizeM2?.let {
            where += "EXISTS (SELECT 1 FROM listing_units u WHERE u.listing_id = l.id AND u.size_m2 IS NOT NULL AND u.size_m2 <= :maxSize)"
            params.addValue("maxSize", it)
        }

        val whereSql = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")

        val orderBy = when (query.sort) {
            ListingQuery.Sort.CLOSING -> "COALESCE(l.application_end, l.application_start, l.announcement_date::timestamptz) ASC NULLS LAST"
            ListingQuery.Sort.ANNOUNCEMENT -> "l.announcement_date DESC NULLS LAST"
            ListingQuery.Sort.MOVE_IN -> "l.move_in_date ASC NULLS LAST"
            // MATCH 정렬은 인메모리에서 처리 — 쿼리는 임박순으로 일단 가져오고 application 레이어에서 재정렬.
            ListingQuery.Sort.MATCH -> "COALESCE(l.application_end, l.announcement_date::timestamptz) ASC NULLS LAST"
            ListingQuery.Sort.PRICE_LOW -> "(SELECT MIN(price_max_krw) FROM listing_units u WHERE u.listing_id = l.id) ASC NULLS LAST"
            ListingQuery.Sort.PRICE_HIGH -> "(SELECT MAX(price_max_krw) FROM listing_units u WHERE u.listing_id = l.id) DESC NULLS LAST"
        }

        val countSql = "SELECT COUNT(*) FROM listings l $whereSql"
        val total = jdbc.queryForObject(countSql, params, Long::class.java) ?: 0L

        params.addValue("limit", query.size).addValue("offset", query.page * query.size)
        val dataSql = """
            SELECT l.id, l.source, l.source_ref, l.listing_type, l.name, l.developer,
                   l.sido, l.sigungu, l.address, l.latitude, l.longitude, l.polygon_geojson::text AS polygon_geojson,
                   l.application_start, l.application_end, l.announcement_date, l.winner_announcement_date,
                   l.contract_start_date, l.contract_end_date, l.move_in_date, l.total_supply, l.raw_document_url
            FROM listings l
            $whereSql
            ORDER BY $orderBy
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        val content = jdbc.query(dataSql, params, listingMapper)
        return ListingPage(content, query.page, query.size, total)
    }

    override fun findByIds(ids: Collection<Long>): List<Listing> {
        if (ids.isEmpty()) return emptyList()
        val sql = """
            SELECT id, source, source_ref, listing_type, name, developer,
                   sido, sigungu, address, latitude, longitude, polygon_geojson::text AS polygon_geojson,
                   application_start, application_end, announcement_date, winner_announcement_date,
                   contract_start_date, contract_end_date, move_in_date, total_supply, raw_document_url
            FROM listings WHERE id IN (:ids)
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("ids", ids), listingMapper)
    }

    override fun findUnitsByListingIds(ids: Collection<Long>): Map<Long, List<ListingUnit>> {
        if (ids.isEmpty()) return emptyMap()
        val rows = jdbc.query(
            "SELECT id, listing_id, model_no, unit_type, size_m2, supply_count, price_max_krw, deposit_amount, monthly_rent FROM listing_units WHERE listing_id IN (:ids)",
            MapSqlParameterSource("ids", ids),
            unitMapper,
        )
        return rows.groupBy { it.listingId }
    }

    override fun findDetail(id: Long): ListingDetail? {
        val listing = jdbc.query(
            """
            SELECT id, source, source_ref, listing_type, name, developer,
                   sido, sigungu, address, latitude, longitude, polygon_geojson::text AS polygon_geojson,
                   application_start, application_end, announcement_date, winner_announcement_date,
                   contract_start_date, contract_end_date, move_in_date, total_supply, raw_document_url
            FROM listings WHERE id = :id
            """.trimIndent(),
            MapSqlParameterSource("id", id),
            listingMapper,
        ).firstOrNull() ?: return null

        val units = jdbc.query(
            "SELECT id, listing_id, model_no, unit_type, size_m2, supply_count, price_max_krw, deposit_amount, monthly_rent FROM listing_units WHERE listing_id = :id ORDER BY id",
            MapSqlParameterSource("id", id),
            unitMapper,
        )
        return ListingDetail(listing, units)
    }
}
