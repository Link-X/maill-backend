#!/bin/bash
# 压测数据生成脚本
# 生成结果：4 个分类 + 1 个场地模板 + 5 个演出 × 3 个场次 = 15 场次 × 400 座位 = 6000 座位
# 座位和价格通过场地模板自动复制，无需逐场次创建
#
# 依赖: jq（brew install jq）
# 用法: bash docs/seed-data.sh
#       bash docs/seed-data.sh --host http://localhost:8081

set -e

ADMIN_HOST="http://localhost:8081"
ROW_COUNT=20
COL_COUNT=20
VIP_ROWS=10   # 前 N 行为 VIP 区

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --host) ADMIN_HOST="$2"; shift ;;
  esac
  shift
done

if ! command -v jq &> /dev/null; then
  echo "[ERROR] 需要 jq，请先安装: brew install jq"
  exit 1
fi

echo ""
echo "================================================="
echo "  抢票系统压测数据生成"
echo "  场地模板: ${ROW_COUNT}行×${COL_COUNT}列，前${VIP_ROWS}行VIP"
echo "  目标: 5演出 × 3场次，座位由模板自动复制"
echo "  Admin: $ADMIN_HOST"
echo "================================================="

# ─── 工具函数 ────────────────────────────────────────

check_response() {
  local resp="$1" tag="$2"
  local code
  code=$(echo "$resp" | jq -r '.code // empty' 2>/dev/null)
  if [ "$code" != "200" ]; then
    echo "[ERROR] $tag 失败: $resp"
    exit 1
  fi
}

# 生成场地座位模板 JSON（roomId 占位，外部替换）
build_room_seats_json() {
  local room_id=$1
  local json='{"roomId":'$room_id',"seats":['
  local first=true
  for row in $(seq 1 $ROW_COUNT); do
    for col in $(seq 1 $COL_COUNT); do
      [ "$first" = true ] && first=false || json+=','
      local area=0
      [ $row -le $VIP_ROWS ] && area=1
      local col_str
      col_str=$(printf "%02d" $col)
      json+='{"rowNo":'$row',"colNo":'$col',"type":1,"areaId":"'$area'","seatName":"'$row'排'$col_str'座"}'
    done
  done
  json+=']}'
  echo "$json"
}

# ─── 演出数据 ─────────────────────────────────────────

SHOW_NAMES=(
  "五月天诺亚方舟世界巡回演唱会"
  "周杰伦嘉年华世界巡回演唱会"
  "德云社甲辰年相声专场"
  "李诞脱口秀全国巡演"
  "中超联赛年度总决赛"
)
# 各演出对应的分类名（脚本会先建分类，再用 name → id 映射回填 categoryId）
SHOW_CATEGORIES=("演唱会" "演唱会" "相声" "脱口秀" "体育")
SHOW_VENUES=(
  "国家体育场鸟巢"
  "上海梅赛德斯奔驰文化中心"
  "天津大剧院"
  "北京工人体育馆"
  "广州天河体育场"
)
# 与 venue 对应的 GB/T 行政区划代码（city 表 seed 数据）
SHOW_CITY_CODES=("110000" "310000" "120000" "110000" "440100")
SHOW_ADDRESSES=(
  "朝阳区奥林匹克公园南路 1 号"
  "浦东新区世博大道 1200 号"
  "和平区曲阜道 87 号"
  "东城区工人体育场北路 1 号"
  "天河区天河路 299 号"
)
SHOW_DESCS=(
  "五月天最震撼的年度演唱会，超震撼舞台效果"
  "周杰伦全球巡演上海站，经典金曲全回归"
  "郭德纲、于谦领衔，年度压轴相声专场"
  "李诞全新单口喜剧专场，笑翻全场"
  "年度最强对决，见证足球荣耀时刻"
)
# 演出扩展字段（duration 分钟 / ageLimit / refundRule）
SHOW_EXTENDS=(
  '{"duration":180,"ageLimit":"6+","refundRule":"开演前24小时可退"}'
  '{"duration":180,"ageLimit":"6+","refundRule":"开演前24小时可退"}'
  '{"duration":120,"ageLimit":"全年龄","refundRule":"开演前24小时可退"}'
  '{"duration":120,"ageLimit":"18+","refundRule":"开演前24小时可退"}'
  '{"duration":110,"ageLimit":"全年龄","refundRule":"开赛前48小时可退"}'
)

SESSION_NAMES=("08月场 2026-08-01" "09月场 2026-09-06" "10月场 2026-10-04")
SESSION_STARTS=("2026-08-01T19:30:00" "2026-09-06T19:30:00" "2026-10-04T19:30:00")
SESSION_ENDS=("2026-08-01T22:00:00"   "2026-09-06T22:00:00"  "2026-10-04T22:00:00")

TOTAL_SEATS=$((ROW_COUNT * COL_COUNT))

# ─── Step 1: 确保分类存在（4 个：演唱会/相声/脱口秀/体育）────

echo ""
echo ">>> [1/5] 创建/确认分类..."

declare -a CATEGORY_NAMES_UNIQUE
CATEGORY_NAMES_UNIQUE=("演唱会" "相声" "脱口秀" "体育")

# name → id 映射用 parallel arrays（bash 3.2 兼容 macOS 默认）
CAT_KEYS=()
CAT_VALS=()

cat_id_by_name() {
  local target="$1"
  for i in "${!CAT_KEYS[@]}"; do
    if [ "${CAT_KEYS[$i]}" = "$target" ]; then
      echo "${CAT_VALS[$i]}"
      return
    fi
  done
}

for idx in "${!CATEGORY_NAMES_UNIQUE[@]}"; do
  CNAME="${CATEGORY_NAMES_UNIQUE[$idx]}"
  # 先查是否已存在（list 接口按 keyword 前缀查），存在则复用
  EXIST_RESP=$(curl -s "$ADMIN_HOST/api/admin/category/list?keyword=$(printf '%s' "$CNAME" | jq -sRr @uri)")
  EXIST_ID=$(echo "$EXIST_RESP" | jq -r --arg n "$CNAME" '.data[]? | select(.name == $n) | .id' | head -n1)

  if [ -n "$EXIST_ID" ] && [ "$EXIST_ID" != "null" ]; then
    CAT_ID="$EXIST_ID"
    printf "  ✓ [复用] %-8s ID=%s\n" "$CNAME" "$CAT_ID"
  else
    RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/category/create" \
      -H "Content-Type: application/json" \
      -d '{
        "name":   "'"$CNAME"'",
        "sort":   '$((idx * 10))',
        "status": 1
      }')
    check_response "$RESP" "创建分类 $CNAME"
    CAT_ID=$(echo "$RESP" | jq -r '.data.id')
    printf "  ✓ [新建] %-8s ID=%s\n" "$CNAME" "$CAT_ID"
  fi
  CAT_KEYS+=("$CNAME")
  CAT_VALS+=("$CAT_ID")
done

# ─── Step 2: 创建场地模板 ─────────────────────────────

echo ""
echo ">>> [2/5] 创建场地模板..."

RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/create" \
  -H "Content-Type: application/json" \
  -d '{
    "name":        "标准演出场地",
    "venue":       "通用",
    "rowCount":    '$ROW_COUNT',
    "colCount":    '$COL_COUNT',
    "description": "压测用标准场地，'$ROW_COUNT'行×'$COL_COUNT'列，前'$VIP_ROWS'行VIP"
  }')
check_response "$RESP" "创建场地"
ROOM_ID=$(echo "$RESP" | jq -r '.data.id')
echo "  ✓ 场地已创建 ID=$ROOM_ID"

# 保存座位模板
SEATS_JSON=$(build_room_seats_json "$ROOM_ID")
RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/seat/batch" \
  -H "Content-Type: application/json" \
  -d "$SEATS_JSON")
check_response "$RESP" "保存座位模板"
echo "  ✓ 座位模板已保存 (${ROW_COUNT}行×${COL_COUNT}列=${TOTAL_SEATS}个)"

# 保存默认价格区域
RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/area/save" \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": '$ROOM_ID',
    "areas": [
      {"areaId":"0","defaultPrice":380.00,"defaultOriginPrice":580.00},
      {"areaId":"1","defaultPrice":880.00,"defaultOriginPrice":1280.00}
    ]
  }')
check_response "$RESP" "保存价格区域"
echo "  ✓ 默认价格已保存 (普通区¥380 | VIP区¥880)"

# ─── Step 3: 创建演出 ─────────────────────────────────

echo ""
echo ">>> [3/5] 创建演出..."
SHOW_IDS=()
for i in "${!SHOW_NAMES[@]}"; do
  CATEGORY_ID=$(cat_id_by_name "${SHOW_CATEGORIES[$i]}")
  if [ -z "$CATEGORY_ID" ]; then
    echo "[ERROR] 找不到分类 '${SHOW_CATEGORIES[$i]}' 的 ID"
    exit 1
  fi

  RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/show/create" \
    -H "Content-Type: application/json" \
    -d '{
      "name":        "'"${SHOW_NAMES[$i]}"'",
      "description": "'"${SHOW_DESCS[$i]}"'",
      "categoryId":  '$CATEGORY_ID',
      "cityCode":    "'"${SHOW_CITY_CODES[$i]}"'",
      "address":     "'"${SHOW_ADDRESSES[$i]}"'",
      "venue":       "'"${SHOW_VENUES[$i]}"'",
      "posterUrl":   "https://example.com/poster'$((i+1))'.jpg",
      "extend":      '"${SHOW_EXTENDS[$i]}"'
    }')
  check_response "$RESP" "创建演出"
  SID=$(echo "$RESP" | jq -r '.data.id')
  SHOW_IDS+=("$SID")
  printf "  ✓ [%d] %-30s ID=%s (cat=%s city=%s)\n" $((i+1)) "${SHOW_NAMES[$i]}" "$SID" "${SHOW_CATEGORIES[$i]}" "${SHOW_CITY_CODES[$i]}"
done

# ─── Step 4: 创建场次（带 roomId，自动复制座位+价格） ──
# 注意：totalSeats / rowCount / colCount 由后端从 Room 自动算，前端不需要也不应该传

TOTAL_SESSIONS=0
echo ""
echo ">>> [4/5] 创建场次（roomId=$ROOM_ID，座位和价格自动复制）..."

SESSION_IDS=()
for show_idx in "${!SHOW_IDS[@]}"; do
  SHOW_ID="${SHOW_IDS[$show_idx]}"
  echo ""
  echo "  演出 「${SHOW_NAMES[$show_idx]}」(ID=$SHOW_ID)"

  for sess_idx in "${!SESSION_NAMES[@]}"; do
    RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/session/create" \
      -H "Content-Type: application/json" \
      -d '{
        "showId":       '$SHOW_ID',
        "roomId":       '$ROOM_ID',
        "name":         "'"${SESSION_NAMES[$sess_idx]}"'",
        "startTime":    "'"${SESSION_STARTS[$sess_idx]}"'",
        "endTime":      "'"${SESSION_ENDS[$sess_idx]}"'",
        "limitPerUser": 4,
        "extend":       {"preSaleLeadMinutes":30}
      }')
    check_response "$RESP" "创建场次"
    SESSION_ID=$(echo "$RESP" | jq -r '.data.id')
    SESSION_IDS+=("$SESSION_ID")
    printf "    ✓ 场次 %-22s ID=%s (座位+价格已自动复制)\n" "${SESSION_NAMES[$sess_idx]}" "$SESSION_ID"
    TOTAL_SESSIONS=$((TOTAL_SESSIONS + 1))
  done
done

# ─── Step 5: 批量发布 + 预热 ──────────────────────────

echo ""
echo ">>> [5/5] 发布并预热 Redis 库存..."
for SESSION_ID in "${SESSION_IDS[@]}"; do
  RESP=$(curl -s -X PUT "$ADMIN_HOST/api/admin/session/$SESSION_ID/publish")
  check_response "$RESP" "发布场次 $SESSION_ID"

  RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/seat/warmup/$SESSION_ID")
  check_response "$RESP" "预热场次 $SESSION_ID"

  echo "  ✓ 场次 ID=$SESSION_ID 已发布并预热"
done

echo ""
echo "================================================="
echo "  生成完成"
printf "  分类数量: %d\n" "${#CAT_KEYS[@]}"
printf "  场地模板: ID=%s (%d行×%d列)\n" "$ROOM_ID" "$ROW_COUNT" "$COL_COUNT"
printf "  演出数量: %d\n" "${#SHOW_IDS[@]}"
printf "  场次数量: %d\n" "$TOTAL_SESSIONS"
printf "  总座位数: %d\n" "$((TOTAL_SESSIONS * TOTAL_SEATS))"
echo "================================================="
