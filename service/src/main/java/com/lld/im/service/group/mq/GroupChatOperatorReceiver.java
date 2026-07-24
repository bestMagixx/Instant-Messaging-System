package com.lld.im.service.group.mq;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.common.model.message.MessageReadContent;
import com.lld.im.service.group.service.GroupMessageService;
import com.lld.im.service.message.service.MessageSyncService;
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
public class GroupChatOperatorReceiver {

    private static Logger logger = LoggerFactory.getLogger(GroupChatOperatorReceiver.class);

    @Autowired
    GroupMessageService groupMessageService;

    @Autowired
    MessageSyncService messageSyncService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.Im2GroupService,durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.Im2GroupService,durable = "true")
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

            if(command.equals(GroupEventCommand.MSG_GROUP.getCommand())){
                //处理消息
                GroupChatMessageContent messageContent = jsonObject.toJavaObject(GroupChatMessageContent.class);
                groupMessageService.process(messageContent);
            }if(command.equals(GroupEventCommand.MSG_GROUP_READED.getCommand())){
                MessageReadContent messageReadContent = JSON.parseObject(msg, new TypeReference<MessageReadContent>() {
                }.getType());
                messageSyncService.groupReadMark(messageReadContent);
            }
            channel.basicAck(deliveryTag, false);
        }catch (Exception e){
            logger.error("处理消息出现异常：{}",e.getMessage());
            logger.error("RMQ_CHAT_TRAN_ERROR",e);
            logger.error("NACK_MSG:{}",msg);
            //第一个false表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag,false,false);
        }
    }

}
