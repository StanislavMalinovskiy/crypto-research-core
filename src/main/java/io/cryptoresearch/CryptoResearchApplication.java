package io.cryptoresearch;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CryptoResearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoResearchApplication.class, args);
	}

	@Bean
	Clock applicationClock() {
		return Clock.systemUTC();
	}

}
