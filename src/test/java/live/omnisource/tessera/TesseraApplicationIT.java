package live.omnisource.tessera;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.main.banner-mode=off",
        // keep tests lightweight; most beans are lazy until used
        "spring.thymeleaf.cache=false"
})
class TesseraApplicationIT {

    @Test
    void contextLoads() {
        // if the Spring context fails to start, this test fails
    }
}
