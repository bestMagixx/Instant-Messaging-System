package com.lld.im.message.config;

import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public PaginationInterceptor paginationIntercepetor(){
        return new PaginationInterceptor();
    }

    @Bean
    public EasySqlInjector easySqlInjector(){
        return new EasySqlInjector();
    }
}
