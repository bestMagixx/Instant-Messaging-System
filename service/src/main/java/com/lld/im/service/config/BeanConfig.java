package com.lld.im.service.config;

import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.ImUrlRouteWayEnum;
import com.lld.im.common.enums.RouteHashMethodEnum;
import com.lld.im.common.route.RouteHandle;
import com.lld.im.common.route.algorithm.consistenthash.AbstractConsistentHash;
import com.lld.im.service.utils.SnowflakeIdWorker;
import org.I0Itec.zkclient.ZkClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.List;

@Configuration
public class BeanConfig {

    @Autowired
    AppConfig appConfig;

    @Bean
    public ZkClient buildZKClient(){
        return new ZkClient(appConfig.getZkAddr(),appConfig.getZkConnectTimeOut());
    }

    @Bean
    public RouteHandle routeHandle() throws Exception {
        Integer imRouteWay = appConfig.getImRouteWay();
        String routeWay = "";

        ImUrlRouteWayEnum handle = ImUrlRouteWayEnum.getHandler(imRouteWay);
        routeWay = handle.getClazz();

        RouteHandle  routeHandle = (RouteHandle) Class.forName(routeWay).newInstance();
        if(handle == ImUrlRouteWayEnum.RAMDOM){
            return routeHandle;
        }else if(handle == ImUrlRouteWayEnum.LOOP){
            return routeHandle;
        }else if(handle == ImUrlRouteWayEnum.HASH){
            Method setHash = Class.forName(routeWay).getMethod("setHash", AbstractConsistentHash.class);
            Integer consistentHashWay = appConfig.getConsistentHashWay();
            String hashWay = "";

            RouteHashMethodEnum hashHandler = RouteHashMethodEnum.getHandler(consistentHashWay);
            hashWay = hashHandler.getClazz();
            AbstractConsistentHash consistentHash = (AbstractConsistentHash) Class.forName(hashWay).newInstance();

            setHash.invoke(routeHandle, consistentHash);
            return routeHandle;
        }

        return null;
    }

    @Bean
    public EasySqlInjector easySqlInjector(){
        return new EasySqlInjector();
    }

    @Bean
    public SnowflakeIdWorker buildSnowflakSeq() throws Exception{
        return new SnowflakeIdWorker(0);
    }

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setPassword("xxx");
        return Redisson.create(config);
    }
}
