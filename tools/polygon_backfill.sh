#!/usr/bin/env bash
# 로컬에서 VWorld 연속지적도 폴리곤 → Railway ingestion 으로 푸시.
# Railway → VWorld Data API 막혀있어서 우회. (지오코딩과 동일 패턴)
#
# 사전조건: VWORLD_API_KEY, INGESTION_ADMIN_TOKEN, jq, curl
# - VWorld Data API 권한 + 연속지적도(LP_PA_CBND_BUBUN) 데이터셋 사용 승인 필요
# - 키에 등록된 URI 와 일치하는 Referer 로 요청

set -euo pipefail

API="${API_BASE_URL:-https://api-production-1d45.up.railway.app}"
INGEST="${INGEST_BASE_URL:-https://ingestion-production-fd56.up.railway.app}"
REFERER="${VWORLD_REFERER:-https://homefit.app/}"
BBOX_DELTA="${BBOX_DELTA:-0.0015}"   # 약 150m

: "${VWORLD_API_KEY:?VWORLD_API_KEY required}"
: "${INGESTION_ADMIN_TOKEN:?INGESTION_ADMIN_TOKEN required}"

echo "fetching listings with coords but no polygon..."
total=$(curl -sS "$API/api/v1/listings?size=1&activeOnly=false" | jq '.total')
echo "total: $total"

batch_size=100
items_json="[]"
items_count=0
ok=0
fail=0
processed=0

pages=$(( (total + batch_size - 1) / batch_size ))
for ((p=0; p<pages; p++)); do
  echo "page $((p+1))/$pages"
  page_data=$(curl -sS "$API/api/v1/listings?activeOnly=false&size=$batch_size&page=$p")
  rows=$(echo "$page_data" | jq -c '.content[] | select(.latitude != null) | {id, latitude, longitude}')

  while IFS= read -r row; do
    [ -z "$row" ] && continue
    id=$(echo "$row" | jq '.id')
    lat=$(echo "$row" | jq '.latitude')
    lng=$(echo "$row" | jq '.longitude')

    # 상세에서 polygon 있는지 확인 — 있으면 skip
    has=$(curl -sS "$API/api/v1/listings/$id" | jq -r '.polygonGeoJson | type')
    if [ "$has" != "null" ]; then continue; fi
    processed=$((processed + 1))

    # bbox 주변 폴리곤 조회
    minLng=$(awk "BEGIN{print $lng - $BBOX_DELTA}")
    minLat=$(awk "BEGIN{print $lat - $BBOX_DELTA}")
    maxLng=$(awk "BEGIN{print $lng + $BBOX_DELTA}")
    maxLat=$(awk "BEGIN{print $lat + $BBOX_DELTA}")

    resp=$(curl -sS -G "https://api.vworld.kr/req/data" \
      -H "Referer: $REFERER" \
      --data-urlencode "service=data" \
      --data-urlencode "request=GetFeature" \
      --data-urlencode "data=LP_PA_CBND_BUBUN" \
      --data-urlencode "geomFilter=BOX($minLng,$minLat,$maxLng,$maxLat)" \
      --data-urlencode "format=json" \
      --data-urlencode "geometry=true" \
      --data-urlencode "size=20" \
      --data-urlencode "key=$VWORLD_API_KEY" \
      --max-time 15 || echo '{}')

    status=$(echo "$resp" | jq -r '.response.status // empty')
    if [ "$status" != "OK" ]; then
      fail=$((fail + 1))
      continue
    fi

    fc=$(echo "$resp" | jq -c '.response.result.featureCollection')
    if [ "$fc" = "null" ] || [ -z "$fc" ]; then
      fail=$((fail + 1))
      continue
    fi

    items_json=$(echo "$items_json" | jq --argjson id "$id" --argjson fc "$fc" \
      '. + [{id: $id, geojson: $fc}]')
    items_count=$((items_count + 1))
    ok=$((ok + 1))
    sleep 0.25
  done <<< "$rows"
done

if [ "$items_count" -eq 0 ]; then
  echo "nothing to push (processed=$processed ok=$ok fail=$fail)"
  exit 0
fi

echo "pushing $items_count polygons..."
# 폴리곤은 좌표가 많아 ARG_MAX 초과 가능 → 파일로 빼고 @로 전송
tmp_body=$(mktemp)
trap 'rm -f "$tmp_body"' EXIT
echo "$items_json" | jq '{items: .}' > "$tmp_body"

# 한 번에 다 보내면 Railway/Cloudflare body 크기 제한 걸릴 수 있어 50건씩 청크
total_items=$(jq '.items | length' < "$tmp_body")
chunk=50
pushed=0
for ((i=0; i<total_items; i+=chunk)); do
  chunk_file=$(mktemp)
  jq --argjson off "$i" --argjson n "$chunk" '{items: (.items[$off:$off+$n])}' < "$tmp_body" > "$chunk_file"
  status=$(curl -sS -o /tmp/_polygon_resp.json -X POST "$INGEST/admin/ingestion/polygons" \
    -H "Content-Type: application/json" \
    -H "X-Admin-Token: $INGESTION_ADMIN_TOKEN" \
    --data-binary "@$chunk_file" -w "%{http_code}")
  rm -f "$chunk_file"
  body=$(cat /tmp/_polygon_resp.json)
  echo "  chunk $((i/chunk + 1)): HTTP $status — $body"
done

echo ""
echo "summary: processed=$processed ok=$ok fail=$fail"
