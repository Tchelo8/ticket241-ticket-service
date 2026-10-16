package com.tchelo.ticket_service_ticket241.repository;

import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Recherche par Identifiants
    Optional<Ticket> findByUuid(String uuid);

    // Billets "vivants" d'un utilisateur pour un événement (en cours de réservation ou payés) :
    // sert à appliquer maxTicketsPerUser de l'événement
    long countByUserIdAndEventIdAndStatusInAndIsArchivedFalse(String userId, Long eventId, List<TicketStatus> statuses);

    // Billets restés PENDING (event-service n'a jamais confirmé les places) créés avant le seuil
    @Query("SELECT t FROM Ticket t WHERE t.status = 'PENDING' " +
            "AND t.createdAt < :threshold " +
            "AND t.isArchived = false")
    List<Ticket> findStalePendingTickets(@Param("threshold") LocalDateTime threshold);

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findByQrCode(String qrCode);

    Optional<Ticket> findBySecurityHash(String securityHash);


    // Recherche par Utilisateur
    Page<Ticket> findByUserIdAndIsArchivedFalseOrderByCreatedAtDesc(
            String userId,
            Pageable pageable
    );

    List<Ticket> findByUserIdAndStatusInAndIsArchivedFalse(
            String userId,
            List<TicketStatus> statuses
    );

    // Commandes non encore payées d'un acheteur (limite les réservations invités qui bloquent des places)
    @Query("SELECT COUNT(DISTINCT t.orderId) FROM Ticket t WHERE t.userId = :userId " +
            "AND t.status IN :statuses AND t.isArchived = false")
    long countOrdersByUserIdAndStatusIn(
            @Param("userId") String userId,
            @Param("statuses") List<TicketStatus> statuses
    );

    @Query("SELECT t FROM Ticket t WHERE t.userId = :userId " +
            "AND t.status = 'PAID' " +
            "AND t.eventStartDate >= :now " +
            "AND t.isArchived = false " +
            "ORDER BY t.eventStartDate ASC")
    Page<Ticket> findUpcomingTicketsByUser(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );




    // Recherche par Événement
    Page<Ticket> findByEventIdAndIsArchivedFalseOrderByCreatedAtDesc(
            Long eventId,
            Pageable pageable
    );

    List<Ticket> findByEventIdAndStatusAndIsArchivedFalse(
            Long eventId,
            TicketStatus status
    );

    long countByEventIdAndStatusAndIsArchivedFalse(
            Long eventId,
            TicketStatus status
    );


    // Recherche par Commande
    List<Ticket> findByOrderIdAndIsArchivedFalse(String orderId);

    // Tous les tickets d'un événement (tous statuts)
    List<Ticket> findByEventIdAndIsArchivedFalse(Long eventId);

    Optional<Ticket> findByOrderIdAndIsPrimaryTicketTrueAndIsArchivedFalse(String orderId);



    Page<Ticket> findByStatusAndIsArchivedFalse(
            TicketStatus status,
            Pageable pageable
    );

    List<Ticket> findByStatusInAndIsArchivedFalse(List<TicketStatus> statuses);


    // Réservations Expirées
    @Query("SELECT t FROM Ticket t WHERE t.status = 'RESERVED' " +
            "AND t.expirationDate < :now " +
            "AND t.isArchived = false")
    List<Ticket> findExpiredReservations(@Param("now") LocalDateTime now);



    List<Ticket> findByPaymentIdAndIsArchivedFalse(String paymentId);

    Optional<Ticket> findByPaymentTransactionIdAndIsArchivedFalse(String transactionId);


    // Statistiques
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.eventId = :eventId " +
            "AND t.status = 'PAID' " +
            "AND t.isArchived = false")
    long countPaidTicketsByEvent(@Param("eventId") Long eventId);

    @Query("SELECT SUM(t.finalPrice) FROM Ticket t WHERE t.eventId = :eventId " +
            "AND t.status = 'PAID' " +
            "AND t.isArchived = false")
    BigDecimal sumRevenueByEvent(@Param("eventId") Long eventId);

    @Query("SELECT t.eventId, COUNT(t) FROM Ticket t " +
            "WHERE t.status = 'PAID' " +
            "AND t.isArchived = false " +
            "GROUP BY t.eventId " +
            "ORDER BY COUNT(t) DESC")
    List<Object[]> findTopEventsByTicketsSold(Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.userId = :userId " +
            "AND t.status = 'PAID' " +
            "AND t.isArchived = false")
    List<Ticket> findAllPaidTicketsByUser(@Param("userId") String userId);


    // Validation et Check-in
    @Query("SELECT t FROM Ticket t WHERE t.qrCode = :qrCode " +
            "AND t.status = 'PAID' " +
            "AND t.isArchived = false")
    Optional<Ticket> findValidTicketByQrCode(@Param("qrCode") String qrCode);

    long countByEventIdAndCheckedInTrueAndIsArchivedFalse(Long eventId);


    // Fraude et Sécurité
    List<Ticket> findByIsSuspiciousTrueAndIsArchivedFalse();

    @Query("SELECT t FROM Ticket t WHERE t.ipAddress = :ipAddress " +
            "AND t.createdAt > :since " +
            "AND t.isArchived = false")
    List<Ticket> findRecentTicketsByIp(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );
}