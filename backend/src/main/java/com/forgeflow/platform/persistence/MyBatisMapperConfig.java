package com.forgeflow.platform.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.forgeflow")
public class MyBatisMapperConfig {
}
