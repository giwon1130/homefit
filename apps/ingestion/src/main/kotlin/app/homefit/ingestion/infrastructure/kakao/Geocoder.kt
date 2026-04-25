package app.homefit.ingestion.infrastructure.kakao

/**
 * 주소 → 좌표 변환 추상화. 구현체는 카카오 / VWorld / 기타.
 * 패키지가 kakao 인 건 역사적 이유 — 후속 정리에서 .geocoder 로 이전 예정.
 */
interface Geocoder {
    fun geocode(address: String): GeoPoint?
}
