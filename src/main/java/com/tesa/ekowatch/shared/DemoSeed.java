package com.tesa.ekowatch.shared;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Component
@Profile("local-demo & !prod & !production")
@ConditionalOnProperty(name = "ekowatch.demo.enabled", havingValue = "true")
public class DemoSeed implements ApplicationRunner {
    private final DataSource source;

    public DemoSeed(DataSource source) {
        this.source = source;
    }

    @Transactional
    public void run(ApplicationArguments args) {
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql")).execute(source);
    }
}
