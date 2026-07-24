package com.lld.im.tcp.receiver;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.MessagePack;
import com.lld.im.common.constant.Constants;
import com.lld.im.tcp.receiver.process.BaseProcess;
import com.lld.im.tcp.receiver.process.ProcessFactory;
import com.lld.im.tcp.utils.MqFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

@Slf4j
public class MessageReceiver {

    private static String brokerId;

    private static void  startReceiverMessage(){
        try {
            Channel channel = MqFactory.getChannel(Constants.RabbitConstants.MessageService2Im + brokerId);
            channel.queueDeclare(Constants.RabbitConstants.MessageService2Im + brokerId,
                    true,false,false,null);
            channel.queueBind(Constants.RabbitConstants.MessageService2Im + brokerId,
                    Constants.RabbitConstants.MessageService2Im,brokerId);

            channel.basicConsume(Constants.RabbitConstants.MessageService2Im + brokerId, false,
                    new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            //TODO 处理消息服务发来的消息
                            try {
                                String msgStr = new String(body);
                                MessagePack messagePack = JSONObject.parseObject(msgStr, MessagePack.class);
                                BaseProcess messageProcess = ProcessFactory.getMessageProcess(messagePack.getCommand());
                                messageProcess.process(messagePack);
                                channel.basicAck(envelope.getDeliveryTag(), false);
                                log.info(msgStr);
                            } catch (Exception e) {
                                e.printStackTrace();
                                channel.basicNack(envelope.getDeliveryTag(),false,false);
                            }
                        }

            });
        } catch (Exception e){

        }
    }

    public static void init(){
        startReceiverMessage();
    }

    public static void init(String brokerId){
        if(StringUtils.isBlank(MessageReceiver.brokerId)){
            MessageReceiver.brokerId = brokerId;
        }
        startReceiverMessage();
    }

}
