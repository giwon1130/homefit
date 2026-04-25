#!/usr/bin/env bash
# 로컬에서 VWorld로 지오코딩 → Railway ingestion 서비스에 좌표 push
# Railway → VWorld 연결이 막혀 있어서 우회용.
#
# 사전조건:
#   - VWORLD_API_KEY 환경변수 (railway variables --service ingestion --kv 로 추출)
#   - INGESTION_ADMIN_TOKEN 환경변수
#   - jq, curl

set -euo pipefail

API="${API_BASE_URL:-https://api-production-1d45.up.railway.app}"
INGEST="${INGEST_BASE_URL:-https://ingestion-production-fd56.up.railway.app}"

: "${VWORLD_API_KEY:?VWORLD_API_KEY required (export from railway variables)}"
: "${INGESTION_ADMIN_TOKEN:?INGESTION_ADMIN_TOKEN required}"

clean_addr() {
  local input="$1"
  # 괄호 안에 '번지' 또는 '일원' 있으면 그게 진짜 주소 (예: "AA36BL(서구 불로동 589번지 일원)")
  local inside
  inside=$(echo "$input" | grep -oE '\([^)]*(번지|일원|동 [0-9])[^)]*\)' | head -1 | sed -E 's/^\(//' | sed -E 's/\)$//')
  if [ -n "$inside" ]; then
    echo "$inside" | sed -E 's/[[:space:]]+(일원|일대|외)$//' | tr -s ' '
    return
  fi
  # 괄호 제거 + 트레일 BL/블록/단지/도시개발구역 등 제거
  echo "$input" \
    | sed -E 's/\([^)]*\)//g' \
    | sed -E 's/,[^,]*$//' \
    | sed -E 's/[[:space:]]+(일원|일대|외)//g' \
    | sed -E 's/[[:space:]]+[A-Z]+-?[0-9]+(BL|블록|단지)?$//' \
    | sed -E 's/[[:space:]]+[0-9]+(BL|블록|단지)$//' \
    | sed -E 's/[[:space:]]+[가-힣]+(택지개발지구|공공주택지구|도시개발구역|구역|에코델타시티|역세권도시개발사업|국제화계획지구).*$//' \
    | tr -s ' '
}

geocode_one() {
  # $1 = 주소
  local addr cleaned
  addr="$1"
  cleaned=$(clean_addr "$addr")
  for type in ROAD PARCEL; do
    local resp
    resp=$(curl -sS -G "https://api.vworld.kr/req/address" \
      --data-urlencode "service=address" \
      --data-urlencode "request=getCoord" \
      --data-urlencode "format=json" \
      --data-urlencode "type=$type" \
      --data-urlencode "address=$cleaned" \
      --data-urlencode "key=$VWORLD_API_KEY" \
      --max-time 10) || continue
    local x y status
    status=$(echo "$resp" | jq -r '.response.status // empty')
    if [ "$status" = "OK" ]; then
      x=$(echo "$resp" | jq -r '.response.result.point.x')
      y=$(echo "$resp" | jq -r '.response.result.point.y')
      echo "$y $x"
      return 0
    fi
  done
  return 1
}

# 1) 좌표 없는 listing 가져오기
echo "fetching all listings..."
total=$(curl -sS "$API/api/v1/listings?size=1&activeOnly=false" | jq '.total')
echo "total: $total"

batch_size=100
items_json="[]"
items_count=0
processed=0
ok=0
fail=0

# 페이지 순회
pages=$(( (total + batch_size - 1) / batch_size ))
for ((p=0; p<pages; p++)); do
  echo "page $((p+1))/$pages"
  page_data=$(curl -sS "$API/api/v1/listings?activeOnly=false&size=$batch_size&page=$p")
  rows=$(echo "$page_data" | jq -c '.content[] | select(.latitude == null) | {id, address}')

  while IFS= read -r row; do
    [ -z "$row" ] && continue
    processed=$((processed + 1))
    id=$(echo "$row" | jq '.id')
    addr=$(echo "$row" | jq -r '.address')
    [ "$addr" = "null" ] || [ -z "$addr" ] && { fail=$((fail+1)); continue; }

    if coords=$(geocode_one "$addr"); then
      lat=$(echo "$coords" | awk '{print $1}')
      lng=$(echo "$coords" | awk '{print $2}')
      items_json=$(echo "$items_json" | jq --argjson id "$id" --arg lat "$lat" --arg lng "$lng" \
        '. + [{id: $id, latitude: ($lat|tonumber), longitude: ($lng|tonumber)}]')
      items_count=$((items_count + 1))
      ok=$((ok + 1))
    else
      fail=$((fail + 1))
    fi
    sleep 0.2  # rate
  done <<< "$rows"
done

if [ "$items_count" -eq 0 ]; then
  echo "nothing to push (processed=$processed ok=$ok fail=$fail)"
  exit 0
fi

echo "pushing $items_count coords to ingestion..."
body=$(jq -n --argjson items "$items_json" '{items: $items}')
curl -sS -X POST "$INGEST/admin/ingestion/coordinates" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $INGESTION_ADMIN_TOKEN" \
  -d "$body" -w "\nHTTP %{http_code}\n"

echo ""
echo "summary: processed=$processed ok=$ok fail=$fail"
