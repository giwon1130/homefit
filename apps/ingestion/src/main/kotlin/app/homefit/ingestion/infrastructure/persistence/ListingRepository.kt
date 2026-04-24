package app.homefit.ingestion.infrastructure.persistence

import app.homefit.ingestion.domain.listing.RawListing
import app.homefit.ingestion.domain.listing.RawListingUnit
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ListingRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    @Transactional
    fun upsert(listing: RawListing): Long {
        val id = upsertListing(listing)
        replaceUnits(id, listing.units)
        return id
    }

    private fun upsertListing(listing: RawListing): Long {
        val sql = """
            INSERT INTO listings (
                source, source_ref, listing_type, name, developer, sido, sigungu, address,
                application_start, application_end, announcement_date, winner_announcement_date,
                contract_start_date, contract_end_date, move_in_date, total_supply,
                raw_document_url, raw_json, updated_at
            ) VALUES (
                :source, :source_ref, :listing_type, :name, :developer, :sido, :sigungu, :address,
                :application_start, :application_end, :announcement_date, :winner_announcement_date,
                :contract_start_date, :contract_end_date, :move_in_date, :total_supply,
                :raw_document_url, CAST(:raw_json AS jsonb), now()
            )
            ON CONFLICT (source, source_ref) DO UPDATE SET
                listing_type             = EXCLUDED.listing_type,
                name                     = EXCLUDED.name,
                developer                = EXCLUDED.developer,
                sido                     = EXCLUDED.sido,
                sigungu                  = EXCLUDED.sigungu,
                address                  = EXCLUDED.address,
                application_start        = EXCLUDED.application_start,
                application_end          = EXCLUDED.application_end,
                announcement_date        = EXCLUDED.announcement_date,
                winner_announcement_date = EXCLUDED.winner_announcement_date,
                contract_start_date      = EXCLUDED.contract_start_date,
                contract_end_date        = EXCLUDED.contract_end_date,
                move_in_date             = EXCLUDED.move_in_date,
                total_supply             = EXCLUDED.total_supply,
                raw_document_url         = EXCLUDED.raw_document_url,
                raw_json                 = EXCLUDED.raw_json,
                updated_at               = now()
            RETURNING id
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("source", listing.source.code)
            .addValue("source_ref", listing.sourceRef)
            .addValue("listing_type", listing.listingType.code)
            .addValue("name", listing.name)
            .addValue("developer", listing.developer)
            .addValue("sido", listing.sido)
            .addValue("sigungu", listing.sigungu)
            .addValue("address", listing.address)
            .addValue("application_start", listing.applicationStart)
            .addValue("application_end", listing.applicationEnd)
            .addValue("announcement_date", listing.announcementDate)
            .addValue("winner_announcement_date", listing.winnerAnnouncementDate)
            .addValue("contract_start_date", listing.contractStartDate)
            .addValue("contract_end_date", listing.contractEndDate)
            .addValue("move_in_date", listing.moveInDate)
            .addValue("total_supply", listing.totalSupply)
            .addValue("raw_document_url", listing.documentUrl)
            .addValue("raw_json", listing.rawJson)

        val keyHolder = GeneratedKeyHolder()
        jdbc.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to get listing id")
    }

    private fun replaceUnits(listingId: Long, units: List<RawListingUnit>) {
        jdbc.update(
            "DELETE FROM listing_units WHERE listing_id = :id",
            MapSqlParameterSource("id", listingId),
        )
        if (units.isEmpty()) return

        val sql = """
            INSERT INTO listing_units (listing_id, model_no, unit_type, size_m2, supply_count, price_max_krw, raw_json)
            VALUES (:listing_id, :model_no, :unit_type, :size_m2, :supply_count, :price_max_krw, CAST(:raw_json AS jsonb))
        """.trimIndent()
        val batch = units.map { u ->
            MapSqlParameterSource()
                .addValue("listing_id", listingId)
                .addValue("model_no", u.modelNo)
                .addValue("unit_type", u.unitType)
                .addValue("size_m2", u.sizeM2)
                .addValue("supply_count", u.supplyCount)
                .addValue("price_max_krw", u.priceMaxKrw)
                .addValue("raw_json", u.rawJson)
        }.toTypedArray()
        jdbc.batchUpdate(sql, batch)
    }
}
