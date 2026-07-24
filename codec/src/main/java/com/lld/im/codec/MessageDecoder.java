package com.lld.im.codec;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.Message;
import com.lld.im.codec.proto.MessageHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {
//    @Override
//    protected void decode(ChannelHandlerContext ctx,
//                          ByteBuf in, List<Object> out) throws Exception {
//        if(in.readableBytes() < 28){
//            return;
//        }
//        /* 获取command */
//        int command = in.readInt();
//
//        /* 获取version */
//        int version = in.readInt();
//
//        /* 获取clientType */
//        int clientType = in.readInt();
//
//        /* 获取messageType */
//        int messageType = in.readInt();
//
//        /* 获取appId */
//        int appId = in.readInt();
//
//        /* 获取imei长度 */
//        int imeiLength = in.readInt();
//
//        /* 获取bodyLen */
//        int bodyLen = in.readInt();
//
//        if(in.readableBytes() < imeiLength + bodyLen){
//            in.resetReaderIndex();
//            return;
//        }
//
//        byte[] imeiData = new byte[imeiLength];
//        in.readBytes(imeiData);
//        String imei = new String(imeiData);
//
//        byte[] bodyData = new byte[bodyLen];
//        in.readBytes(bodyData);
//
//
//        MessageHeader messageHeader = new MessageHeader();
//        messageHeader.setCommand(command);
//        messageHeader.setVersion(version);
//        messageHeader.setMessageType(messageType);
//        messageHeader.setAppId(appId);
//        messageHeader.setImeiLength(imeiLength);
//        messageHeader.setImei(imei);
//
//        Message message = new Message();
//        message.setMessageHeader(messageHeader);
//
//        if(messageHeader.getMessageType() == 0x0){
//            String body = new String(bodyData);
//            JSONObject parse = (JSONObject) JSONObject.parse(body);
//            message.setMessagePack(parse);
//        }
//
//        in.markReaderIndex();
//        out.add(message);
//    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

        // 1. 协议头固定 28 字节，不够直接返回
        if (in.readableBytes() < 28) {
            return;
        }

        // 2. 标记读取位置，数据不足时恢复
        in.markReaderIndex();

        // 3. 读取 28 字节协议头
        int command     = in.readInt();
        int version     = in.readInt();
        int clientType  = in.readInt();
        int messageType = in.readInt();
        int appId       = in.readInt();
        int imeiLength  = in.readInt();
        int bodyLen     = in.readInt();

        // 4. 检查剩余数据是否足够
        if (in.readableBytes() < imeiLength + bodyLen) {
            in.resetReaderIndex();
            return;
        }

        // 5. 读取 imei + body
        byte[] imeiData = new byte[imeiLength];
        in.readBytes(imeiData);

        byte[] bodyData = new byte[bodyLen];
        in.readBytes(bodyData);

        // 6. 封装 header
        MessageHeader header = new MessageHeader();
        header.setCommand(command);
        header.setVersion(version);
        header.setClientType(clientType);
        header.setMessageType(messageType);
        header.setAppId(appId);
        header.setImeiLength(imeiLength);
        header.setImei(new String(imeiData));

        Message message = new Message();
        message.setMessageHeader(header);

        // ===================== 【修复点】 =====================
        // 解析 BODY 数据，不是解析 IMEI！！！
        if (messageType == 0x0) {
            String bodyStr = new String(bodyData); // 这里修复了！
            JSONObject json = JSONObject.parseObject(bodyStr);
            message.setMessagePack(json);
        }

        // 输出到业务 Handler
        out.add(message);
    }
}
