package com.lightdrone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 운영/로컬 DB(PostgreSQL)가 아니라 격리된 인메모리 H2(test 프로파일)로 컨텍스트를 로드한다.
// 실제 DB·Flyway 연결 없이 결정적으로 실행되도록 보장한다.
@SpringBootTest
@ActiveProfiles("test")
class LightdroneApplicationTests {

    @Test
    void contextLoads() {
    }
}
