package app.homefit.ingestion.infrastructure.publicdata.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** odcloud 공통 응답 래퍼 (분양정보). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplyhomeAptListingsResponse(
    val currentCount: Int = 0,
    val matchCount: Int = 0,
    val page: Int = 1,
    val perPage: Int = 0,
    val totalCount: Int = 0,
    val data: List<ApplyhomeAptItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplyhomeAptModelsResponse(
    val currentCount: Int = 0,
    val matchCount: Int = 0,
    val page: Int = 1,
    val perPage: Int = 0,
    val totalCount: Int = 0,
    val data: List<ApplyhomeAptModelItem> = emptyList(),
)

/** getAPTLttotPblancDetail — 공고 단위 레코드. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplyhomeAptItem(
    @JsonProperty("HOUSE_MANAGE_NO") val houseManageNo: String? = null,
    @JsonProperty("PBLANC_NO") val pblancNo: String? = null,
    @JsonProperty("HOUSE_NM") val houseName: String? = null,
    @JsonProperty("SUBSCRPT_AREA_CODE") val areaCode: String? = null,
    @JsonProperty("SUBSCRPT_AREA_CODE_NM") val areaName: String? = null,
    @JsonProperty("HSSPLY_ADRES") val address: String? = null,
    @JsonProperty("HOUSE_SECD") val houseType: String? = null,
    @JsonProperty("HOUSE_SECD_NM") val houseTypeName: String? = null,
    @JsonProperty("HOUSE_DTL_SECD") val houseDetailType: String? = null,
    @JsonProperty("HOUSE_DTL_SECD_NM") val houseDetailTypeName: String? = null,
    @JsonProperty("RENT_SECD") val rentType: String? = null,
    @JsonProperty("RENT_SECD_NM") val rentTypeName: String? = null,
    @JsonProperty("RCRIT_PBLANC_DE") val announcementDate: String? = null,
    @JsonProperty("RCEPT_BGNDE") val applicationStartDate: String? = null,
    @JsonProperty("RCEPT_ENDDE") val applicationEndDate: String? = null,
    @JsonProperty("PRZWNER_PRESNATN_DE") val winnerAnnouncementDate: String? = null,
    @JsonProperty("CNTRCT_CNCLS_BGNDE") val contractStartDate: String? = null,
    @JsonProperty("CNTRCT_CNCLS_ENDDE") val contractEndDate: String? = null,
    @JsonProperty("MVN_PREARNGE_YM") val moveInYearMonth: String? = null,
    @JsonProperty("TOT_SUPLY_HSHLDCO") val totalSupply: Int? = null,
    @JsonProperty("BSNS_MBY_NM") val developer: String? = null,
    @JsonProperty("CNSTRCT_ENTRPS_NM") val constructor: String? = null,
    @JsonProperty("MDHS_TELNO") val contactPhone: String? = null,
    @JsonProperty("PBLANC_URL") val documentUrl: String? = null,
    @JsonProperty("HMPG_ADRES") val homepageUrl: String? = null,
)

/** getAPTLttotPblancMdl — 주택형별 레코드 (평형, 공급세대수, 분양가). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplyhomeAptModelItem(
    @JsonProperty("HOUSE_MANAGE_NO") val houseManageNo: String? = null,
    @JsonProperty("PBLANC_NO") val pblancNo: String? = null,
    @JsonProperty("MODEL_NO") val modelNo: String? = null,
    @JsonProperty("HOUSE_TY") val houseTy: String? = null,       // 주택형 (예: 84.92A)
    @JsonProperty("SUPLY_AR") val supplyArea: String? = null,     // 공급면적 (㎡, 문자열)
    @JsonProperty("SUPLY_HSHLDCO") val supplyCount: Int? = null,
    @JsonProperty("LTTOT_TOP_AMOUNT") val topAmountThousandKrw: Long? = null, // 최고분양가 (천원 단위)
)
