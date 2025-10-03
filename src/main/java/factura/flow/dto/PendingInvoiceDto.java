package factura.flow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa un registro de factura pendiente de la base de datos.
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
