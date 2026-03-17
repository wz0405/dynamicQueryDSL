package com.example.dsl;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.dsl.mapper")
public class DynamicQuerySpecApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicQuerySpecApplication.class, args);
    }
}
