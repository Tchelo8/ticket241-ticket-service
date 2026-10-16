package com.tchelo.ticket_service_ticket241.repository;

import com.tchelo.ticket_service_ticket241.entity.GuestAccessSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface GuestAccessSessionRepository extends JpaRepository<GuestAccessSession, Long> {

    Optional<GuestAccessSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM GuestAccessSession s WHERE s.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
