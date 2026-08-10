package com.tongcheng;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.tongcheng.mapper")
@SpringBootApplication
public class TongChengApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongChengApplication.class, args);
    }

}
