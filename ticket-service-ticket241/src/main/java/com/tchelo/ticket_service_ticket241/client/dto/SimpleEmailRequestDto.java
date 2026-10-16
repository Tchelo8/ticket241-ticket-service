package com.tchelo.ticket_service_ticket241.client.dto;

// Corps de POST /api/notifications/internal/email (notification-service)
public record SimpleEmailRequestDto(String to, String subject, String body) {
}
