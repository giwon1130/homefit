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
        )
    }

    override fun search(query: ListingQuery): ListingPage {
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource()

        query.sido?.let { where += "sido = :sido"; params.addValue("sido", it) }
        query.sigungu?.let { where += "sigungu = :sigungu"; params.addValue("sigungu", it) }
        if (query.types.isNotEmpty()) {
            where += "listing_type IN (:types)"
            params.addValue("types", query.types.map { it.name })
        }
        if (query.activeOnly) {
            // 접수 마감이 아직 안 끝났거나, 공고일이 최근 60일 이내인 것
            where += "(application_end IS NULL OR application_end > now() OR announcement_date > current_date - INTERVAL '60 days')"
        }

        val whereSql = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")

        val orderBy = when (query.sort) {
            ListingQuery.Sort.CLOSING -> "COALESCE(application_end, application_start, announcement_date::timestamptz) ASC NULLS LAST"
            ListingQuery.Sort.ANNOUNCEMENT -> "announcement_date DESC NULLS LAST"
            ListingQuery.Sort.MOVE_IN -> "move_in_date ASC NULLS LAST"
            // MATCH 정렬은 인메모리에서 처리 — 쿼리는 임박순으로 일단 가져오고 application 레이어에서 재정렬.
            ListingQuery.Sort.MATCH -> "COALESCE(application_end, announcement_date::timestamptz) ASC NULLS LAST"
        }

        val countSql = "SELECT COUNT(*) FROM listings $whereSql"
        val total = jdbc.queryForObject(countSql, params, Long::class.java) ?: 0L

        params.addValue("limit", query.size).addValue("offset", query.page * query.size)
        val dataSql = """
            SELECT id, source, source_ref, listing_type, name, developer,
                   sido, sigungu, address, latitude, longitude,
                   application_start, application_end, announcement_date, winner_announcement_date,
                   contract_start_date, contract_end_date, move_in_date, total_supply, raw_document_url
            FROM listings
            $whereSql
            ORDER BY $orderBy
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        val content = jdbc.query(dataSql, params, listingMapper)
        return ListingPage(content, query.page, query.size, total)
    }

    override fun findUnitsByListingIds(ids: Collection<Long>): Map<Long, List<ListingUnit>> {
        if (ids.isEmpty()) return emptyMap()
        val rows = jdbc.query(
            "SELECT id, listing_id, model_no, unit_type, size_m2, supply_count, price_max_krw FROM listing_units WHERE listing_id IN (:ids)",
            MapSqlParameterSource("ids", ids),
            unitMapper,
        )
        return rows.groupBy { it.listingId }
    }

    override fun findDetail(id: Long): ListingDetail? {
        val listing = jdbc.query(
            """
            SELECT id, source, source_ref, listing_type, name, developer,
                   sido, sigungu, address, latitude, longitude,
                   application_start, application_end, announcement_date, winner_announcement_date,
                   contract_start_date, contract_end_date, move_in_date, total_supply, raw_document_url
            FROM listings WHERE id = :id
            """.trimIndent(),
            MapSqlParameterSource("id", id),
            listingMapper,
        ).firstOrNull() ?: return null

        val units = jdbc.query(
            "SELECT id, listing_id, model_no, unit_type, size_m2, supply_count, price_max_krw FROM listing_units WHERE listing_id = :id ORDER BY id",
            MapSqlParameterSource("id", id),
            unitMapper,
        )
        return ListingDetail(listing, units)
    }
}
