package com.lld.im.message.mq;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.constant.Constants;
import com.lld.im.message.dao.ImMessageBodyEntity;
import com.lld.im.message.model.DoStoreGroupMessageDto;
import com.lld.im.message.model.DoStoreP2PMessageDto;
import com.lld.im.message.service.StoreMessageService;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class StoreGroupMessageReceiver {
    private static Logger logger = LoggerFactory.getLogger(StoreGroupMessageReceiver.class);

    @Autowired
    StoreMessageService storeMessageService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.StoreGroupMessage,durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.StoreGroupMessage,durable = "true")
            ),concurrency = "1"
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String, Object> headers,
                              Channel channel) throws IOException {
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = (long) headers.get(AmqpHeaders.DELIVERY_TAG);
        logger.info("CHAT MSG FROM QUEUE ::: {}",msg);

        try{
            JSONObject jsonObject = JSONObject.parseObject(msg);

            DoStoreGroupMessageDto dto = jsonObject.toJavaObject(DoStoreGroupMessageDto.class);
            ImMessageBodyEntity messageBodyEntity = jsonObject.getObject("messageBody", ImMessageBodyEntity.class);
            dto.setImMessageBodyEntity(messageBodyEntity);
            storeMessageService.doStoreGroupMessage(dto);

            channel.basicAck(deliveryTag,false);
        }catch (Exception e){
            logger.error("处理消息出现异常：{}",e.getMessage());
            logger.error("RMQ_CHAT_TRAN_ERROR",e);
            logger.error("NACK_MSG:{}",msg);
            //第一个false表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag,false,false);
        }
    }

}
