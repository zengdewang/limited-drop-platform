#!/usr/bin/env bash
# 秒杀链路端到端冒烟：注册→登录→建商品→建发售→开售预热→抢购→幂等→轮询建单→支付→评价
# 用法: bash scripts/smoke-chain.sh
set -uo pipefail

OPS=limiteddrop-ops-2026
RAND=$RANDOM
USERNAME="smoke_${RAND}"
echo "### 0. 用户名: $USERNAME"

echo "### 1. 注册用户"
REG=$(curl -s -X POST http://localhost:8081/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Test1234!\"}")
echo "$REG"
UID_=$(echo "$REG" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')
[ -z "$UID_" ] && { echo "!! 注册失败"; exit 1; }
echo "userId=$UID_"

echo "### 2. 登录（验证密码校验）"
curl -s -X POST http://localhost:8081/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Test1234!\"}" | head -c 300; echo

echo "### 3. 创建商品"
PROD=$(curl -s -X POST http://localhost:8082/api/product/products \
  -H "Content-Type: application/json" -H "X-Ops-Key: $OPS" \
  --data-binary @scripts/smoke-payload.json)
echo "$PROD"
PID=$(echo "$PROD" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "productId=$PID"

echo "### 4. 创建发售（drop，stock=10）"
DROP=$(curl -s -X POST http://localhost:8082/api/product/drops \
  -H "Content-Type: application/json" -H "X-Ops-Key: $OPS" \
  -d "{\"productId\":$PID,\"name\":\"DEMO DROP $RAND\",\"startTime\":\"2026-08-25T20:00:00\",\"endTime\":\"2026-08-25T23:59:59\",\"stock\":10,\"priceCents\":1680000}")
echo "$DROP"
DID=$(echo "$DROP" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "dropId=$DID"

echo "### 5. 等 drop_session 同步后开售（预热库存）"
sleep 3
curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/open" -H "X-Ops-Key: $OPS"; echo
curl -s "http://localhost:8083/api/flashsale/drops/$DID/info"; echo

echo "### 6. 抢购（应命中 code=0）"
BUY1=$(curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/buy" -H "X-User-Id: $UID_")
echo "$BUY1"
ONO=$(echo "$BUY1" | sed -n 's/.*"orderNo":"\([^"]*\)".*/\1/p')

echo "### 7. 同用户重复抢购（应 code=-2 且同 orderNo）"
curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/buy" -H "X-User-Id: $UID_"; echo

echo "### 8. 轮询订单（404→CREATING 继续，200→PENDING_PAYMENT）"
for i in 1 2 3 4 5 6; do
  CODE=$(curl -s -o /tmp/smoke_ord.json -w "%{http_code}" "http://localhost:8084/api/orders/$ONO" -H "X-User-Id: $UID_")
  echo "  poll#$i http=$CODE body=$(cat /tmp/smoke_ord.json | head -c 200)"
  [ "$CODE" = "200" ] && break
  sleep 1
done

echo "### 9. 支付（应 PAID）"
curl -s -X POST "http://localhost:8084/api/orders/$ONO/pay" -H "X-User-Id: $UID_"; echo

echo "### 10. 等 OrderPaidEvent 同步 paid_order 后评价（应 APPROVED）"
sleep 2
sed "s/__PLACEHOLDER__/$ONO/" scripts/smoke-review.json > /tmp/smoke_review.json
curl -s -X POST http://localhost:8082/api/product/reviews \
  -H "Content-Type: application/json" -H "X-User-Id: $UID_" \
  --data-binary @/tmp/smoke_review.json; echo

echo "### 11. 查 paid_order 与 review"
docker exec drop-mysql sh -c "mysql -uroot -proot123 --default-character-set=utf8mb4 -e 'USE drop_product_db; SELECT order_no,customer_id,product_id FROM paid_order ORDER BY id DESC LIMIT 3; SELECT order_no,rating,status FROM review ORDER BY id DESC LIMIT 3;'" 2>/dev/null

echo "### 12. 售罄分支（drain 掉剩余 9 件）"
for i in $(seq 1 11); do
  BUY=$(curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/buy" -H "X-User-Id: $((UID_+i))")
  echo "  user$i => $(echo "$BUY" | head -c 120)"
done

echo "### DONE"
