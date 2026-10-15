package com.tchelo.ticket_service_ticket241;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
@EnableFeignClients
public class TicketServiceTicket241Application {

	public static void main(String[] args) {
		SpringApplication.run(TicketServiceTicket241Application.class, args);
	}

}
