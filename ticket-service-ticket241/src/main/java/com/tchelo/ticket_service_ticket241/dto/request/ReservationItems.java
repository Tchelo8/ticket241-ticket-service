package com.tchelo.ticket_service_ticket241.dto.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Lignes d'une réservation (compte ou invité) : la liste "items", ou à défaut l'ancien couple
// catégorie + quantité. Une catégorie citée deux fois est regroupée en une seule ligne.
public final class ReservationItems {

    private ReservationItems() {
    }

    public static List<ReservationItemDto> resolve(List<ReservationItemDto> items, Long categoryId, Integer quantity) {
        List<ReservationItemDto> source = items != null && !items.isEmpty()
                ? items
                : categoryId != null && quantity != null ? List.of(new ReservationItemDto(categoryId, quantity)) : List.of();

        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (ReservationItemDto item : source) {
            if (item == null || item.getTicketCategoryId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                continue;
            }
            merged.merge(item.getTicketCategoryId(), item.getQuantity(), Integer::sum);
        }

        List<ReservationItemDto> resolved = new ArrayList<>();
        merged.forEach((id, qty) -> resolved.add(new ReservationItemDto(id, qty)));
        return resolved;
    }

    public static int total(List<ReservationItemDto> items) {
        return items.stream().map(ReservationItemDto::getQuantity).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
