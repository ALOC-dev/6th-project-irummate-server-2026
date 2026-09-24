package com.irummate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:irummate-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=none",
		"jwt.secret=irummate-test-secret-key-with-at-least-32-bytes",
		"jwt.access-token-expiration=3600000",
		"jwt.refresh-token-expiration=1209600000"
})
class IrummateApplicationTests {

	@Test
	void contextLoads() {
	}

}
