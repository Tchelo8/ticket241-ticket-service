package com.tchelo.ticket_service_ticket241.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HomeController {
    @GetMapping("/hello")
    public String SayWelcomeMessage() {
        return "Welcome to Ticket Service";
    }
}
