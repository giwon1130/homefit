package app.homefit.domain.listing

enum class ListingType {
    PRIVATE_SALE,       // 민영 분양
    PUBLIC_SALE,        // 공공 분양
    NEWLYWED_HOPE,      // 신혼희망타운
    HAPPY_HOUSE,        // 행복주택
    PURCHASE_RENTAL,    // 매입임대
    JEONSE_RENTAL,      // 전세임대
    NATIONAL_RENTAL,    // 국민임대
    OTHER,
    ;
    companion object {
        fun parse(code: String?): ListingType = runCatching { valueOf(code!!) }.getOrDefault(OTHER)
    }
}

/**
 * 자격 유형 — 한 공고 안의 공급 슬롯 구분.
 */
enum class SupplyType(val label: String) {
    GENERAL("일반공급"),
    FIRST_TIME("생애최초 특별공급"),
    NEWLYWED("신혼부부 특별공급"),
    MULTI_CHILD("다자녀 특별공급"),
    ;
}
