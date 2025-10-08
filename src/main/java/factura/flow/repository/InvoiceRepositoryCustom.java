package factura.flow.repository;

import factura.flow.dto.PendingInvoiceDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interfaz personalizada para operaciones personalizadas de repositorio de facturas.
 * Las implementaciones de esta interfaz proporcionan funcionalidad adicional
 * más allá de las operaciones CRUD estándar.
 */
public interface InvoiceRepositoryCustom {
    
    /**
     * Mapea los resultados de la consulta a objetos PendingInvoiceDto.
     * Incluye registro de métricas y manejo de errores.
     * 
     * @return Lista de DTOs de facturas pendientes
     * @throws org.springframework.dao.DataAccessException si ocurre un error al procesar los resultados
     */
    List<PendingInvoiceDto> findPendingInvoiceDtos();
    
    /**
     * Obtiene la última fecha de procesamiento desde Redis
     * @return La última fecha de procesamiento o la fecha por defecto si no existe
     */
    LocalDateTime getLastProcessedDate();
    
    /**
     * Establece la última fecha de procesamiento en Redis
     * @param date La fecha a establecer como última fecha de procesamiento
     */
    void setLastProcessedDate(LocalDateTime date);
}
