package com.lld.im.tcp.receiver;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.MessagePack;
import com.lld.im.common.ClientType;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.DeviceMultiLoginEnum;
import com.lld.im.common.enums.command.SystemCommand;
import com.lld.im.common.model.UserClientDTO;
import com.lld.im.tcp.redis.RedisManager;
import com.lld.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @description
 * 多端登录：1单端登录：一端在线，踢掉除了本clientType + imei的设备
 *         2.双端登录：允许pc/mobile其中一端登录 + web端
 *         3.三端登录：允许手机+pc+web，踢掉同端的其他imei除了web
 *         4.不做任何处理
 *
 */
public class UserLoginMessageListener {

    private final static Logger logger = LoggerFactory.getLogger(UserLoginMessageListener.class);

    private Integer loginModel;


    public UserLoginMessageListener(Integer loginModel) {
        this.loginModel = loginModel;
    }

    public void listenerUserLogin(){
        RTopic topic = RedisManager.getRedissonClient().getTopic(Constants.RedisConstants.UserLoginChannel);
        topic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence charSequence, String msg) {
                logger.info("收到用户上线通知：" + msg);
                UserClientDTO dto = JSONObject.parseObject(msg, UserClientDTO.class);
                List<NioSocketChannel> channels = SessionSocketHolder.get(dto.getAppId(), dto.getUserId());

                for (NioSocketChannel nioSocketChannel : channels){
                    if(loginModel == DeviceMultiLoginEnum.ONE.getLoginMode()){
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();
                        String clientImei = dto.getClientType() + ":" + dto.getImei();

                        if(!(clientType + ":" + imei).equals(dto.getClientType() + ":" + dto.getImei())){
                            //TODO 踢掉客户端
                            //告诉客户端 其他端登录
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }else if(loginModel == DeviceMultiLoginEnum.TWO.getLoginMode()){
                        if(dto.getClientType() == ClientType.WEB.getCode()){
                            return;
                        }
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                        if(clientType == ClientType.WEB.getCode()){
                            continue;
                        }
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();
                        if(!(clientType + ":" + imei).equals(dto.getClientType() + ":" + dto.getImei())){
                            //TODO 踢掉客户端
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }else if(loginModel == DeviceMultiLoginEnum.THREE.getLoginMode()){
                        if(dto.getClientType() == ClientType.WEB.getCode()){
                            return;
                        }
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();

                        Boolean isSameClient = false;
                        if((clientType == ClientType.IOS.getCode() || clientType == ClientType.ANDROID.getCode())
                            && (dto.getClientType() == ClientType.IOS.getCode() || dto.getClientType() == ClientType.ANDROID.getCode())){
                            isSameClient = true;
                        }

                        if((clientType == ClientType.MAC.getCode() || clientType == ClientType.WINDOWS.getCode())
                                && (dto.getClientType() == ClientType.MAC.getCode() || dto.getClientType() == ClientType.WINDOWS.getCode())){
                            isSameClient = true;
                        }
                        if(isSameClient && !(clientType + ":" + imei).equals(dto.getClientType() + ":" + dto.getImei())){
                            //TODO 踢掉客户端
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }
                }
            }
        });

    }
}
