package app.homefit.ingestion.infrastructure.publicdata

import app.homefit.ingestion.domain.listing.ListingSource
import app.homefit.ingestion.domain.listing.ListingType
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptItem
import app.homefit.ingestion.infrastructure.publicdata.dto.ApplyhomeAptModelItem
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ApplyhomeMapperTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val mapper = ApplyhomeMapper(objectMapper)

    @Test
    fun `maps APT 분양 item to RawListing`() {
        val item = ApplyhomeAptItem(
            houseManageNo = "2026000123",
            pblancNo = "2026000123",
            houseName = "○○자이",
            areaName = "서울",
            address = "서울특별시 서초구 반포동 123-4",
            houseDetailTypeName = "민영",
            rentTypeName = "분양주택",
            announcementDate = "2026-04-10",
            applicationStartDate = "2026-04-21",
            applicationEndDate = "2026-04-23",
            moveInYearMonth = "202808",
            totalSupply = 850,
            developer = "○○건설",
            documentUrl = "https://example.com/a.pdf",
        )
        val models = listOf(
            ApplyhomeAptModelItem(
                houseManageNo = "2026000123",
                modelNo = "1",
                houseTy = "84.92A",
                supplyArea = "84.92",
                supplyCount = 120,
                topAmountThousandKrw = 1_500_000L, // 15억
            ),
        )

        val raw = mapper.toRawListing(item, models)!!

        assertThat(raw.source).isEqualTo(ListingSource.PUBLIC_DATA_APT)
        assertThat(raw.sourceRef).isEqualTo("2026000123")
        assertThat(raw.listingType).isEqualTo(ListingType.PRIVATE_SALE)
        assertThat(raw.sido).isEqualTo("서울")
        assertThat(raw.sigungu).isEqualTo("서초구")
        assertThat(raw.announcementDate).isEqualTo(LocalDate.of(2026, 4, 10))
        assertThat(raw.moveInDate).isEqualTo(LocalDate.of(2028, 8, 1))
        assertThat(raw.applicationStart!!.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 21))
        assertThat(raw.applicationEnd!!.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 23))
        assertThat(raw.totalSupply).isEqualTo(850)
        assertThat(raw.units).hasSize(1)
        assertThat(raw.units[0].priceMaxKrw).isEqualTo(1_500_000_000L)  // 15억
        assertThat(raw.units[0].supplyCount).isEqualTo(120)
    }

    @Test
    fun `skips item without houseManageNo`() {
        val item = ApplyhomeAptItem(houseName = "x")
        assertThat(mapper.toRawListing(item, emptyList())).isNull()
    }

    @Test
    fun `classifies 신혼희망타운`() {
        val item = ApplyhomeAptItem(
            houseManageNo = "1", houseName = "n",
            houseDetailTypeName = "신혼희망타운",
            rentTypeName = "분양주택",
        )
        val raw = mapper.toRawListing(item, emptyList())!!
        assertThat(raw.listingType).isEqualTo(ListingType.NEWLYWED_HOPE)
    }
}
