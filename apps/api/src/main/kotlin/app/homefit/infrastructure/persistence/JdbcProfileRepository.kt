package app.homefit.infrastructure.persistence

import app.homefit.domain.profile.Assets
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HouseholdRelation
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import app.homefit.domain.profile.ProfileRepository
import app.homefit.domain.profile.Residence
import app.homefit.domain.profile.Workplace
import app.homefit.domain.profile.WorkplaceOwner
import app.homefit.infrastructure.crypto.AesGcmEncryptor
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.time.LocalTime
import java.time.OffsetDateTime

@Repository
class JdbcProfileRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val enc: AesGcmEncryptor,
) : ProfileRepository {

    // ---------- CORE ----------

    override fun findCore(userId: Long): ProfileCore? {
        val sql = """
            SELECT birth_date, marriage_date, is_householder, is_first_time_buyer,
                   no_home_since, subscription_account_opened_at,
                   subscription_deposit_months, subscription_deposit_total
            FROM user_profiles WHERE user_id = :uid
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("uid", userId)) { rs, _ ->
            ProfileCore(
                birthDate = rs.getDate("birth_date")?.toLocalDate(),
                marriageDate = rs.getDate("marriage_date")?.toLocalDate(),
                isHouseholder = rs.getBooleanOrNull("is_householder"),
                isFirstTimeBuyer = rs.getBooleanOrNull("is_first_time_buyer"),
                noHomeSince = rs.getDate("no_home_since")?.toLocalDate(),
                subscriptionAccountOpenedAt = rs.getDate("subscription_account_opened_at")?.toLocalDate(),
                subscriptionDepositMonths = rs.getIntOrNull("subscription_deposit_months"),
                subscriptionDepositTotal = rs.getLongOrNull("subscription_deposit_total"),
            )
        }.firstOrNull()
    }

    @Transactional
    override fun saveCore(userId: Long, core: ProfileCore) {
        val sql = """
            INSERT INTO user_profiles (
                user_id, birth_date, marriage_date, is_householder, is_first_time_buyer,
                no_home_since, subscription_account_opened_at,
                subscription_deposit_months, subscription_deposit_total, updated_at
            ) VALUES (
                :uid, :birth, :marriage, :householder, :first_time,
                :no_home, :acct_opened, :deposit_months, :deposit_total, now()
            )
            ON CONFLICT (user_id) DO UPDATE SET
                birth_date                     = EXCLUDED.birth_date,
                marriage_date                  = EXCLUDED.marriage_date,
                is_householder                 = EXCLUDED.is_householder,
                is_first_time_buyer            = EXCLUDED.is_first_time_buyer,
                no_home_since                  = EXCLUDED.no_home_since,
                subscription_account_opened_at = EXCLUDED.subscription_account_opened_at,
                subscription_deposit_months    = EXCLUDED.subscription_deposit_months,
                subscription_deposit_total     = EXCLUDED.subscription_deposit_total,
                updated_at                     = now()
        """.trimIndent()
        jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("birth", core.birthDate)
                .addValue("marriage", core.marriageDate)
                .addValue("householder", core.isHouseholder)
                .addValue("first_time", core.isFirstTimeBuyer)
                .addValue("no_home", core.noHomeSince)
                .addValue("acct_opened", core.subscriptionAccountOpenedAt)
                .addValue("deposit_months", core.subscriptionDepositMonths)
                .addValue("deposit_total", core.subscriptionDepositTotal),
        )
    }

    // ---------- HOUSEHOLD MEMBERS ----------

    private val memberMapper = RowMapper { rs: ResultSet, _ ->
        HouseholdMember(
            id = rs.getLong("id"),
            relation = HouseholdRelation.valueOf(rs.getString("relation")),
            birthDate = rs.getDate("birth_date")?.toLocalDate(),
        )
    }

    override fun findHouseholdMembers(userId: Long): List<HouseholdMember> =
        jdbc.query(
            "SELECT id, relation, birth_date FROM household_members WHERE user_id = :uid ORDER BY id",
            MapSqlParameterSource("uid", userId), memberMapper,
        )

    @Transactional
    override fun replaceHouseholdMembers(userId: Long, members: List<HouseholdMember>) {
        jdbc.update("DELETE FROM household_members WHERE user_id = :uid", MapSqlParameterSource("uid", userId))
        if (members.isEmpty()) return
        val batch = members.map { m ->
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("relation", m.relation.name)
                .addValue("birth", m.birthDate)
        }.toTypedArray()
        jdbc.batchUpdate(
            "INSERT INTO household_members (user_id, relation, birth_date) VALUES (:uid, :relation, :birth)",
            batch,
        )
    }

    // ---------- INCOMES (encrypted) ----------

    override fun findIncomes(userId: Long): List<Income> =
        jdbc.query(
            "SELECT year, self_amount_enc, spouse_amount_enc FROM incomes WHERE user_id = :uid ORDER BY year",
            MapSqlParameterSource("uid", userId),
        ) { rs, _ ->
            Income(
                year = rs.getInt("year"),
                selfAmount = enc.decryptLong(rs.getBytes("self_amount_enc")),
                spouseAmount = enc.decryptLong(rs.getBytes("spouse_amount_enc")),
            )
        }

    @Transactional
    override fun replaceIncomes(userId: Long, incomes: List<Income>) {
        jdbc.update("DELETE FROM incomes WHERE user_id = :uid", MapSqlParameterSource("uid", userId))
        if (incomes.isEmpty()) return
        val batch = incomes.map { i ->
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("year", i.year)
                .addValue("self_enc", enc.encryptLong(i.selfAmount))
                .addValue("spouse_enc", enc.encryptLong(i.spouseAmount))
        }.toTypedArray()
        jdbc.batchUpdate(
            "INSERT INTO incomes (user_id, year, self_amount_enc, spouse_amount_enc) VALUES (:uid, :year, :self_enc, :spouse_enc)",
            batch,
        )
    }

    // ---------- ASSETS (encrypted) ----------

    override fun findAssets(userId: Long): Assets? =
        jdbc.query(
            "SELECT net_worth_enc, real_estate_enc, monthly_debt_amount_enc, updated_at FROM assets WHERE user_id = :uid",
            MapSqlParameterSource("uid", userId),
        ) { rs, _ ->
            Assets(
                netWorth = enc.decryptLong(rs.getBytes("net_worth_enc")),
                realEstate = enc.decryptLong(rs.getBytes("real_estate_enc")),
                monthlyDebt = enc.decryptLong(rs.getBytes("monthly_debt_amount_enc")),
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            )
        }.firstOrNull()

    @Transactional
    override fun saveAssets(userId: Long, assets: Assets) {
        val sql = """
            INSERT INTO assets (user_id, net_worth_enc, real_estate_enc, monthly_debt_amount_enc, updated_at)
            VALUES (:uid, :net_enc, :real_enc, :debt_enc, now())
            ON CONFLICT (user_id) DO UPDATE SET
                net_worth_enc           = EXCLUDED.net_worth_enc,
                real_estate_enc         = EXCLUDED.real_estate_enc,
                monthly_debt_amount_enc = EXCLUDED.monthly_debt_amount_enc,
                updated_at              = now()
        """.trimIndent()
        jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("net_enc", enc.encryptLong(assets.netWorth))
                .addValue("real_enc", enc.encryptLong(assets.realEstate))
                .addValue("debt_enc", enc.encryptLong(assets.monthlyDebt)),
        )
    }

    // ---------- RESIDENCES ----------

    private val residenceMapper = RowMapper { rs: ResultSet, _ ->
        Residence(
            id = rs.getLong("id"),
            address = rs.getString("address"),
            sido = rs.getString("sido"),
            sigungu = rs.getString("sigungu"),
            dongCode = rs.getString("dong_code"),
            latitude = rs.getBigDecimal("latitude"),
            longitude = rs.getBigDecimal("longitude"),
            livedSince = rs.getDate("lived_since")?.toLocalDate(),
            isCurrent = rs.getBoolean("is_current"),
        )
    }

    override fun findResidences(userId: Long): List<Residence> =
        jdbc.query(
            "SELECT id, address, sido, sigungu, dong_code, latitude, longitude, lived_since, is_current FROM residences WHERE user_id = :uid ORDER BY is_current DESC, id",
            MapSqlParameterSource("uid", userId), residenceMapper,
        )

    @Transactional
    override fun replaceResidences(userId: Long, residences: List<Residence>) {
        jdbc.update("DELETE FROM residences WHERE user_id = :uid", MapSqlParameterSource("uid", userId))
        if (residences.isEmpty()) return
        val batch = residences.map { r ->
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("address", r.address)
                .addValue("sido", r.sido)
                .addValue("sigungu", r.sigungu)
                .addValue("dong_code", r.dongCode)
                .addValue("latitude", r.latitude)
                .addValue("longitude", r.longitude)
                .addValue("lived_since", r.livedSince)
                .addValue("is_current", r.isCurrent)
        }.toTypedArray()
        jdbc.batchUpdate(
            """
            INSERT INTO residences (user_id, address, sido, sigungu, dong_code, latitude, longitude, lived_since, is_current)
            VALUES (:uid, :address, :sido, :sigungu, :dong_code, :latitude, :longitude, :lived_since, :is_current)
            """.trimIndent(),
            batch,
        )
    }

    // ---------- WORKPLACES ----------

    private val workplaceMapper = RowMapper { rs: ResultSet, _ ->
        Workplace(
            id = rs.getLong("id"),
            owner = WorkplaceOwner.valueOf(rs.getString("owner")),
            label = rs.getString("label"),
            address = rs.getString("address"),
            latitude = rs.getBigDecimal("latitude"),
            longitude = rs.getBigDecimal("longitude"),
            arrivalTime = rs.getTime("arrival_time")?.toLocalTime() ?: LocalTime.of(9, 0),
        )
    }

    override fun findWorkplaces(userId: Long): List<Workplace> =
        jdbc.query(
            "SELECT id, owner, label, address, latitude, longitude, arrival_time FROM workplaces WHERE user_id = :uid ORDER BY id",
            MapSqlParameterSource("uid", userId), workplaceMapper,
        )

    @Transactional
    override fun replaceWorkplaces(userId: Long, workplaces: List<Workplace>) {
        jdbc.update("DELETE FROM workplaces WHERE user_id = :uid", MapSqlParameterSource("uid", userId))
        if (workplaces.isEmpty()) return
        val batch = workplaces.map { w ->
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("owner", w.owner.name)
                .addValue("label", w.label)
                .addValue("address", w.address)
                .addValue("latitude", w.latitude)
                .addValue("longitude", w.longitude)
                .addValue("arrival_time", java.sql.Time.valueOf(w.arrivalTime))
        }.toTypedArray()
        jdbc.batchUpdate(
            """
            INSERT INTO workplaces (user_id, owner, label, address, latitude, longitude, arrival_time)
            VALUES (:uid, :owner, :label, :address, :latitude, :longitude, :arrival_time)
            """.trimIndent(),
            batch,
        )
    }

    // ---------- PREFERENCES ----------

    override fun findPreferences(userId: Long): Preferences? =
        jdbc.query(
            """
            SELECT max_purchase_price, max_jeonse_price, max_monthly_rent, max_deposit_for_rent,
                   min_size_m2, max_size_m2, min_rooms, max_commute_minutes, preferred_sidos
            FROM preferences WHERE user_id = :uid
            """.trimIndent(),
            MapSqlParameterSource("uid", userId),
        ) { rs, _ ->
            Preferences(
                maxPurchasePrice = rs.getLongOrNull("max_purchase_price"),
                maxJeonsePrice = rs.getLongOrNull("max_jeonse_price"),
                maxMonthlyRent = rs.getIntOrNull("max_monthly_rent"),
                maxDepositForRent = rs.getLongOrNull("max_deposit_for_rent"),
                minSizeM2 = rs.getBigDecimal("min_size_m2"),
                maxSizeM2 = rs.getBigDecimal("max_size_m2"),
                minRooms = rs.getIntOrNull("min_rooms"),
                maxCommuteMinutes = rs.getIntOrNull("max_commute_minutes"),
                preferredSidos = (rs.getArray("preferred_sidos")?.toStringArray()).orEmpty(),
            )
        }.firstOrNull()

    @Transactional
    override fun savePreferences(userId: Long, preferences: Preferences) {
        val sql = """
            INSERT INTO preferences (
                user_id, max_purchase_price, max_jeonse_price, max_monthly_rent, max_deposit_for_rent,
                min_size_m2, max_size_m2, min_rooms, max_commute_minutes, preferred_sidos, updated_at
            ) VALUES (
                :uid, :mpp, :mjp, :mmr, :mdr,
                :min_size, :max_size, :min_rooms, :max_commute, :sidos, now()
            )
            ON CONFLICT (user_id) DO UPDATE SET
                max_purchase_price   = EXCLUDED.max_purchase_price,
                max_jeonse_price     = EXCLUDED.max_jeonse_price,
                max_monthly_rent     = EXCLUDED.max_monthly_rent,
                max_deposit_for_rent = EXCLUDED.max_deposit_for_rent,
                min_size_m2          = EXCLUDED.min_size_m2,
                max_size_m2          = EXCLUDED.max_size_m2,
                min_rooms            = EXCLUDED.min_rooms,
                max_commute_minutes  = EXCLUDED.max_commute_minutes,
                preferred_sidos      = EXCLUDED.preferred_sidos,
                updated_at           = now()
        """.trimIndent()
        val conn = jdbc.jdbcTemplate.dataSource!!.connection
        val sidosArray = conn.use { it.createArrayOf("text", preferences.preferredSidos.toTypedArray()) }
        jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("mpp", preferences.maxPurchasePrice)
                .addValue("mjp", preferences.maxJeonsePrice)
                .addValue("mmr", preferences.maxMonthlyRent)
                .addValue("mdr", preferences.maxDepositForRent)
                .addValue("min_size", preferences.minSizeM2)
                .addValue("max_size", preferences.maxSizeM2)
                .addValue("min_rooms", preferences.minRooms)
                .addValue("max_commute", preferences.maxCommuteMinutes)
                .addValue("sidos", sidosArray),
        )
    }

    // ---------- HOUSING HISTORY ----------

    private val historyMapper = RowMapper { rs: ResultSet, _ ->
        HousingHistory(
            id = rs.getLong("id"),
            ownedFrom = rs.getDate("owned_from").toLocalDate(),
            ownedTo = rs.getDate("owned_to")?.toLocalDate(),
            note = rs.getString("note"),
        )
    }

    override fun findHousingHistory(userId: Long): List<HousingHistory> =
        jdbc.query(
            "SELECT id, owned_from, owned_to, note FROM housing_history WHERE user_id = :uid ORDER BY owned_from DESC",
            MapSqlParameterSource("uid", userId), historyMapper,
        )

    @Transactional
    override fun replaceHousingHistory(userId: Long, history: List<HousingHistory>) {
        jdbc.update("DELETE FROM housing_history WHERE user_id = :uid", MapSqlParameterSource("uid", userId))
        if (history.isEmpty()) return
        val batch = history.map { h ->
            MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("owned_from", h.ownedFrom)
                .addValue("owned_to", h.ownedTo)
                .addValue("note", h.note)
        }.toTypedArray()
        jdbc.batchUpdate(
            "INSERT INTO housing_history (user_id, owned_from, owned_to, note) VALUES (:uid, :owned_from, :owned_to, :note)",
            batch,
        )
    }
}

// ------ helpers ------

private fun ResultSet.getBooleanOrNull(col: String): Boolean? {
    val v = getBoolean(col)
    return if (wasNull()) null else v
}

private fun ResultSet.getIntOrNull(col: String): Int? {
    val v = getInt(col)
    return if (wasNull()) null else v
}

private fun ResultSet.getLongOrNull(col: String): Long? {
    val v = getLong(col)
    return if (wasNull()) null else v
}

private fun SqlArray.toStringArray(): List<String> =
    @Suppress("UNCHECKED_CAST")
    (array as Array<Any?>).mapNotNull { it as? String }
