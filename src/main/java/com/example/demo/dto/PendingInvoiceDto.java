package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a pending invoice record from the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInvoiceDto {
    private Long idSolicitudActos;
    private String factEstadoFactura;
    private String provincia;
}
