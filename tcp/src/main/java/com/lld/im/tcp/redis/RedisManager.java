package com.lld.im.tcp.redis;

import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.tcp.receiver.UserLoginMessageListener;
import org.redisson.api.RedissonClient;

public class RedisManager {

    private static RedissonClient redissonClient;

    public static void init(BootstrapConfig config){
        SingleClientStrategy singleClientStrategy = new SingleClientStrategy();
        redissonClient = singleClientStrategy.getRedissonClient(config.getLim().getRedis());
        UserLoginMessageListener userLoginMessageListener = new UserLoginMessageListener(config.getLim().getLoginModel());
        userLoginMessageListener.listenerUserLogin();
    }

    public static RedissonClient getRedissonClient(){
        return redissonClient;
    }
}
