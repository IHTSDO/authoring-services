package org.ihtsdo.authoringservices;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@PropertySource(value = "classpath:application-test.properties", encoding = "UTF-8")
public class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
