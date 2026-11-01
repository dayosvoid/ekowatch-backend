package com.tesa.ekowatch.shared;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Component
@Profile("local-demo & !prod & !production")
@ConditionalOnProperty(name = "ekowatch.demo.enabled", havingValue = "true")
public class DemoSeed implements ApplicationRunner {
    private final DataSource source;
    private final Environment environment;

    public DemoSeed(DataSource source, Environment environment) {
        this.source = source;
        this.environment = environment;
    }

    @Transactional
    public void run(ApplicationArguments args) {
        // Defence in depth: manual invocation must not bypass the bean registration guards.
        if (!environment.acceptsProfiles(Profiles.of("local-demo & !prod & !production")) ||
                !Boolean.TRUE.equals(environment.getProperty("ekowatch.demo.enabled", Boolean.class, false)))
            throw new IllegalStateException("Demo seeding requires explicit local-demo opt-in without production profiles");
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql")).execute(source);
    }
}
