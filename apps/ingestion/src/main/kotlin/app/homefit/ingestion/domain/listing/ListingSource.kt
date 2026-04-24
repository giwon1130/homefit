package app.homefit.ingestion.domain.listing

enum class ListingSource(val code: String) {
    PUBLIC_DATA_APT("PUBLIC_DATA_APT"),
    LH("LH"),
    SH("SH"),
    MANUAL("MANUAL"),
    ;
}

/**
 * 공급 유형을 상위 카테고리로 분류. 자격 판정 엔진 (Phase 1-C) 에서 세부 분기.
 */
enum class ListingType(val code: String) {
    PRIVATE_SALE("PRIVATE_SALE"),        // 민영 분양
    PUBLIC_SALE("PUBLIC_SALE"),          // 공공 분양
    NEWLYWED_HOPE("NEWLYWED_HOPE"),      // 신혼희망타운
    HAPPY_HOUSE("HAPPY_HOUSE"),          // 행복주택
    PURCHASE_RENTAL("PURCHASE_RENTAL"),  // 매입임대
    JEONSE_RENTAL("JEONSE_RENTAL"),      // 전세임대
    NATIONAL_RENTAL("NATIONAL_RENTAL"),  // 국민임대
    OTHER("OTHER"),
    ;

    companion object {
        /**
         * 공공데이터포털 응답의 HOUSE_DTL_SECD_NM / RENT_SECD_NM 으로부터 분류.
         * 매핑은 실데이터 확인 후 Phase 1 중 튜닝.
         */
        fun fromCheongyakHome(houseDetailTypeName: String?, rentTypeName: String?): ListingType {
            val rent = rentTypeName?.trim().orEmpty()
            val detail = houseDetailTypeName?.trim().orEmpty()
            return when {
                "신혼희망" in detail -> NEWLYWED_HOPE
                "행복주택" in detail -> HAPPY_HOUSE
                "공공" in detail && "분양" in rent -> PUBLIC_SALE
                "국민임대" in detail -> NATIONAL_RENTAL
                "매입" in detail -> PURCHASE_RENTAL
                "전세" in detail -> JEONSE_RENTAL
                "분양" in rent -> PRIVATE_SALE
                else -> OTHER
            }
        }
    }
}
