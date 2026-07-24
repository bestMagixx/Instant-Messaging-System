package com.lld.im.service.message.mq;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.common.model.message.MessageReadContent;
import com.lld.im.common.model.message.MessageReceiveAckContent;
import com.lld.im.common.model.message.RecallMessageContent;
import com.lld.im.service.message.service.MessageSyncService;
import com.lld.im.service.message.service.P2PMessageService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ChatOperatorReceiver {

    private static Logger logger = LoggerFactory.getLogger(ChatOperatorReceiver.class);

    @Autowired
    P2PMessageService p2PMessageService;

    @Autowired
    MessageSyncService messageSyncService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.Im2MessageService,durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.Im2MessageService,durable = "true")
            ),concurrency = "1"
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String, Object> headers,
                              Channel channel) throws IOException {
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = (long) headers.get(AmqpHeaders.DELIVERY_TAG);
        channel.basicAck(deliveryTag, false);
        logger.info("CHAT MSG FROM QUEUE ::: {}",msg);

        try{
            JSONObject jsonObject = JSONObject.parseObject(msg);
            Integer command = jsonObject.getInteger("command");

            if(command.equals(MessageCommand.MSG_P2P.getCommand())){
                //处理消息
                MessageContent messageContent = jsonObject.toJavaObject(MessageContent.class);
                //TODO 此处可以将消息推送到MQ来让接收者对消息进行鉴别
                //若消息不合规（诈骗，色情），
                // 可以让接收者调用我们暴露出来的消息撤回接口来撤回消息
                //method
                p2PMessageService.process(messageContent);
            }else if(command.equals(MessageCommand.MSG_RECIVE_ACK.getCommand())){
                //消息接受确认
                MessageReceiveAckContent messageContent = jsonObject.toJavaObject(MessageReceiveAckContent.class);
                messageSyncService.receiveMark(messageContent);
            }else if(command.equals(MessageCommand.MSG_READED.getCommand())){
                //消息已读
                MessageReadContent messageReadContent =
                        jsonObject.toJavaObject(MessageReadContent.class);
                messageSyncService.readMark(messageReadContent);
            }else if(command.equals(MessageCommand.MSG_RECALL.getCommand())){
                //撤回消息
                RecallMessageContent messageContent = JSON.parseObject(msg, new TypeReference<RecallMessageContent>() {
                }.getType());
                messageSyncService.recallMessage(messageContent);
            }

        }catch (Exception e){
            logger.error("处理消息出现异常：{}",e.getMessage());
            logger.error("RMQ_CHAT_TRAN_ERROR",e);
            logger.error("NACK_MSG:{}",msg);
            //第一个false表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag,false,false);
        }
    }

}
