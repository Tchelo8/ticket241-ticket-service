package com.tchelo.ticket_service_ticket241.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferTicketDto {

    @NotBlank(message = "L'ID du nouveau propriétaire est obligatoire")
    @Size(max = 100, message = "L'ID du nouveau propriétaire ne peut pas dépasser 100 caractères")
    private String newUserId;

    @Email(message = "Email du nouveau propriétaire invalide")
    @Size(max = 100, message = "L'email ne peut pas dépasser 100 caractères")
    private String newUserEmail;

    @Pattern(
            regexp = "^\\+?[0-9]{10,15}$",
            message = "Numéro de téléphone invalide (format: +24174740465)"
    )
    private String newUserPhone;

    @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères")
    private String newUserFirstName;

    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String newUserLastName;

    @Size(max = 500, message = "La raison du transfert ne peut pas dépasser 500 caractères")
    private String transferReason;
}