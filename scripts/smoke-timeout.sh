#!/usr/bin/env bash
# 支付超时 → 库存释放验证（需 flashsale/order 用 1 分钟窗口参数重启）
set -uo pipefail
OPS=limiteddrop-ops-2026
RAND=$RANDOM
USER="tm_${RAND}"
echo "### user=$USER"

echo "### 1. 注册"
REG=$(curl -s -X POST http://localhost:8081/api/user/auth/register -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Test1234!\"}")
echo "$REG"
UID_=$(echo "$REG" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')
[ -z "$UID_" ] && { echo "!! 注册失败"; exit 1; }

echo "### 2. 建商品"
PROD=$(curl -s -X POST http://localhost:8082/api/product/products -H "Content-Type: application/json" \
  -H "X-Ops-Key: $OPS" --data-binary @scripts/smoke-payload.json)
PID=$(echo "$PROD" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "productId=$PID"

echo "### 3. 建发售（stock=2）"
DROP=$(curl -s -X POST http://localhost:8082/api/product/drops -H "Content-Type: application/json" \
  -H "X-Ops-Key: $OPS" \
  -d "{\"productId\":$PID,\"name\":\"TIMEOUT TEST\",\"startTime\":\"2026-08-25T20:00:00\",\"endTime\":\"2026-08-25T23:59:59\",\"stock\":2,\"priceCents\":99900}")
DID=$(echo "$DROP" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "dropId=$DID"

echo "### 4. 开售"
sleep 3
curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/open" -H "X-Ops-Key: $OPS"; echo

echo "### 5. 抢购（应 code=0，remaining=1）"
BUY=$(curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/buy" -H "X-User-Id: $UID_")
echo "$BUY"
ONO=$(echo "$BUY" | sed -n 's/.*"orderNo":"\([^"]*\)".*/\1/p')
echo "orderNo=$ONO"

echo "### 6. 轮询到 PENDING_PAYMENT"
for i in 1 2 3 4 5; do
  C=$(curl -s -o /tmp/tm_ord.json -w "%{http_code}" "http://localhost:8084/api/orders/$ONO" -H "X-User-Id: $UID_")
  echo "  poll#$i http=$C $(head -c 160 /tmp/tm_ord.json)"
  [ "$C" = "200" ] && break
  sleep 1
done

echo "### 7. 不支付，等 70s（1 分钟窗口 + 余量）让延迟检查触发"
sleep 70

echo "### 8. 订单状态（应 EXPIRED）"
curl -s "http://localhost:8084/api/orders/$ONO" -H "X-User-Id: $UID_"; echo

echo "### 9. 库存（应回补到 2）"
curl -s "http://localhost:8083/api/flashsale/drops/$DID/info"; echo

echo "### 10. 同一用户再次抢购（应 code=0，说明库存已释放且可重入）"
curl -s -X POST "http://localhost:8083/api/flashsale/drops/$DID/buy" -H "X-User-Id: $UID_"; echo

echo "### DONE"
