package com.example.dsl.config;

import com.example.dsl.validator.DynamicQueryValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ValidatorConfig {

    @Bean
    public DynamicQueryValidator dynamicQueryValidator(JdbcTemplate jdbcTemplate) {
        DynamicQueryValidator validator = new DynamicQueryValidator(jdbcTemplate);
        validator.setMode(DynamicQueryValidator.Mode.WARN);
        return validator;
    }
}
