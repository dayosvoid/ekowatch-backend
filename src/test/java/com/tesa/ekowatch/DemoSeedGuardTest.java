package com.tesa.ekowatch;

import com.tesa.ekowatch.shared.DemoSeed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DemoSeedGuardTest {
    @Test void beanRequiresBothOptInsAndProductionAlwaysWins() {
        DataSource source=mock(DataSource.class);
        var runner=new ApplicationContextRunner().withUserConfiguration(DemoSeed.class).withBean(DataSource.class,()->source);
        runner.withPropertyValues("spring.profiles.active=local-demo","ekowatch.demo.enabled=true")
                .run(c->assertThat(c).hasSingleBean(DemoSeed.class));
        runner.withPropertyValues("spring.profiles.active=local-demo").run(c->assertThat(c).doesNotHaveBean(DemoSeed.class));
        runner.withPropertyValues("ekowatch.demo.enabled=true").run(c->assertThat(c).doesNotHaveBean(DemoSeed.class));
        for(String profiles:new String[]{"prod","production","local-demo,prod","local-demo,production","local-demo,prod,production"})
            runner.withPropertyValues("spring.profiles.active="+profiles,"ekowatch.demo.enabled=true").run(c->assertThat(c).doesNotHaveBean(DemoSeed.class));
        runner.withPropertyValues("spring.profiles.active=local-demo","ekowatch.demo.enabled=false").run(c->assertThat(c).doesNotHaveBean(DemoSeed.class));
        verifyNoInteractions(source);
    }
    @Test void directInvocationCannotBypassProductionOrOptInGuards() {
        DataSource source=mock(DataSource.class);
        for(String[] profiles:new String[][]{{},{"local-demo","prod"},{"local-demo","production"}}) {
            var environment=new MockEnvironment().withProperty("ekowatch.demo.enabled","true"); environment.setActiveProfiles(profiles);
            assertThrows(IllegalStateException.class,()->new DemoSeed(source,environment).run(new DefaultApplicationArguments()));
        }
        var disabled=new MockEnvironment(); disabled.setActiveProfiles("local-demo");
        assertThrows(IllegalStateException.class,()->new DemoSeed(source,disabled).run(new DefaultApplicationArguments()));
        verifyNoInteractions(source);
    }
}
