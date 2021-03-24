package org.ihtsdo.authoringservices;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;

@SpringBootTest(args = "--spring.profiles.active=test")
public class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
