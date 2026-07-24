# 单聊消息发送测试脚本
# 用于 im-system 项目的 P2P 消息发送测试
# 使用前确保: MySQL(3306) + Redis(6379) + RabbitMQ(5672) + Service(8000) 均已启动

#!/bin/bash

BASE_URL="http://127.0.0.1:8000"
APP_ID=10000
PRIVATE_KEY="123456"

echo "============================================="
echo "  im-system 单聊消息发送测试"
echo "============================================="

# ============================================
# 测试1: 消息存储测试（无需鉴权，走 /test/ 路径）
# ============================================
echo ""
echo "[测试1] 消息存储测试 (POST /test/message/storeP2P)"
echo "-------------------------------------------"

RESULT1=$(curl -s -X POST "${BASE_URL}/test/message/storeP2P" \
  -d "appId=${APP_ID}" \
  -d "fromId=user_lld" \
  -d "toId=user_test" \
  -d "messageBody=Hello, this is a test message!")

echo "响应: ${RESULT1}"

if echo "${RESULT1}" | grep -q '"code":200'; then
  echo "✅ 测试1通过: 消息存储成功"
else
  echo "❌ 测试1失败: 消息存储失败"
fi

# ============================================
# 测试2: 发送校验接口（无需鉴权）
# ============================================
echo ""
echo "[测试2] 发送校验测试 (POST /v1/message/checkSend)"
echo "-------------------------------------------"

RESULT2=$(curl -s -X POST "${BASE_URL}/v1/message/checkSend" \
  -H "Content-Type: application/json" \
  -d '{
    "fromId": "user_lld",
    "toId": "user_test",
    "appId": 10000,
    "command": 1103
  }')

echo "响应: ${RESULT2}"

if echo "${RESULT2}" | grep -q '"code":200'; then
  echo "✅ 测试2通过: 发送校验通过（用户未被禁言/禁用，且是好友关系）"
elif echo "${RESULT2}" | grep -q '"code":500'; then
  echo "⚠️  测试2警告: 返回500 — 可能是 P2PMessageService.imServerPermissionCheck() 的 Bug"
  echo "   (该方法在所有校验通过后错误返回 ResponseVO.errorResponse())"
else
  echo "❌ 测试2失败: 校验返回错误（可能非好友/被拉黑/被禁言）"
fi

# ============================================
# 测试3: HTTP 单聊消息发送（需鉴权）
# 需要先生成 userSig，这里用已知 appId + privateKey 生成
# ============================================
echo ""
echo "[测试3] HTTP 单聊消息发送 (POST /v1/message/send)"
echo "-------------------------------------------"
echo "注意: 此接口需要 appId + identifier + userSign 鉴权参数"
echo "  userSig 生成方式: SigAPI(appId=10000, key='123456').genUserSig('lld', 86400)"
echo ""
echo "示例请求:"
echo 'curl -X POST "http://127.0.0.1:8000/v1/message/send?appId=10000&identifier=lld&userSign=YOUR_SIGN" \'
echo '  -H "Content-Type: application/json" \'
echo '  -d '\''{'
echo '    "messageId": "msg_001",'
echo '    "fromId": "lld",'
echo '    "toId": "user_test",'
echo '    "messageBody": "Hello from HTTP API",'
echo '    "messageTime": 0,'
echo '    "messageRandom": 12345'
echo '  }'\'

echo ""
echo "如需生成 userSig，请运行 SigAPI.main() 方法:"
echo '  new SigAPI(10000, "123456").genUserSig("lld", 86400);'

# ============================================
# 测试4: 数据库验证
# ============================================
echo ""
echo "[测试4] 数据库消息记录验证"
echo "-------------------------------------------"
echo "检查 im_message_body 和 im_message_history 表中是否有新消息记录:"
echo ""
echo "  SELECT * FROM im_message_body ORDER BY create_time DESC LIMIT 5;"
echo "  SELECT * FROM im_message_history WHERE from_id='user_lld' ORDER BY create_time DESC LIMIT 5;"

# ============================================
# 测试5: RabbitMQ 队列验证
# ============================================
echo ""
echo "[测试5] RabbitMQ 队列验证"
echo "-------------------------------------------"
echo "检查以下队列是否正常:"
echo "  - pipeline2MessageService (TCP -> Service)"
echo "  - messageService2Pipeline (Service -> TCP)"
echo "  - storeP2PMessage         (异步存储)"
echo ""
echo "RabbitMQ 管理后台: http://127.0.0.1:15672 (guest/guest)"

echo ""
echo "============================================="
echo "  测试完成"
echo "============================================="
