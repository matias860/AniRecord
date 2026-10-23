package com.animelist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:anime_list_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.hikari.data-source-properties.sslmode=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.main.lazy-initialization=false",
	"app.cookie-secure=false"
})
class BackendApplicationTests {
	@Autowired HikariDataSource dataSource;

	@Test
	void contextLoads() {
		assertEquals(0, dataSource.getMinimumIdle());
		assertEquals(60_000, dataSource.getIdleTimeout());
		assertEquals(0, dataSource.getKeepaliveTime());
	}

}
