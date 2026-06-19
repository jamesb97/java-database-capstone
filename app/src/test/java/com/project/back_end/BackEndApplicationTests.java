package com.project.back_end;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Full context load requires the remaining service/controller stubs to be "
        + "implemented and reachable MySQL/MongoDB instances. Re-enable once the "
        + "backend is fully wired and datasource properties point at running databases.")
class BackEndApplicationTests {

	@Test
	void contextLoads() {
	}

}
