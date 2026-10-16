package com.tchelo.ticket_service_ticket241.repository;

import com.tchelo.ticket_service_ticket241.entity.GuestAccessCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface GuestAccessCodeRepository extends JpaRepository<GuestAccessCode, Long> {

    // Dernier code envoyé : seul lui est accepté (un nouveau code remplace l'ancien)
    Optional<GuestAccessCode> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    @Modifying
    @Query("DELETE FROM GuestAccessCode c WHERE c.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
