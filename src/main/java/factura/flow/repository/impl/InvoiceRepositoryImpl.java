package factura.flow.repository.impl;

import factura.flow.dto.PendingInvoiceDto;
import factura.flow.repository.InvoiceRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Implementación personalizada del repositorio de facturas con manejo de logs y métricas.
 * Proporciona métodos personalizados que no están disponibles en el repositorio estándar.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InvoiceRepositoryImpl implements InvoiceRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    
    private static final String PENDING_INVOICES_QUERY = """
        SELECT 
            fe.idSolicitudActos as idSolicitudActos, 
            fe.factEstadoFactura as factEstadoFactura, 
            n.provincia as provincia
        FROM [NOTARIAL_JAVA].[Pesnot].[FacturasElectronicas] fe
        INNER JOIN [NOTARIAL_JAVA].[Pesnot].[SolicitudActos] sa ON fe.idSolicitudActos = sa.id
        INNER JOIN [PESNOT].[UBI].[Notarias] n ON n.idNotaria = sa.idNotaria
        WHERE fe.factEstadoFactura IS NULL OR fe.factEstadoFactura <> 'COMPLETADO'
    """;

    /**
     * Ejecuta la consulta SQL para obtener las facturas pendientes.
     * @return Lista de arreglos de objetos con los resultados de la consulta
     */
    private List<Object[]> findPendingInvoices() {
        log.debug("Consultando facturas pendientes...");
        try {
            List<Object[]> results = jdbcTemplate.query(PENDING_INVOICES_QUERY, (rs, rowNum) -> 
                new Object[]{
                    rs.getLong("idSolicitudActos"),
                    rs.getString("factEstadoFactura"),
                    rs.getString("provincia")
                }
            );
            log.debug("Consulta completada. Facturas encontradas: {}", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error en consulta de facturas: {}", e.getMessage());
            log.debug("Stack trace:", e);
            throw new DataAccessException("Error al consultar facturas pendientes", e) {};
        }
    }

    /**
     * {@inheritDoc}
     * 
     * Recupera las facturas pendientes de procesar y las mapea a DTOs.
     * Incluye manejo de errores y registro de métricas de rendimiento.
     * 
     * @return Lista de DTOs de facturas pendientes
     * @throws org.springframework.dao.DataAccessException si ocurre un error al acceder a los datos
     */
    @Override
    public List<PendingInvoiceDto> findPendingInvoiceDtos() {
        log.info("Iniciando proceso de facturas pendientes");
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Buscando facturas pendientes...");
            List<Object[]> results = findPendingInvoices();
            
            log.debug("Mapeando {} facturas a DTOs", results.size());
            List<PendingInvoiceDto> dtos = results.stream()
                .map(row -> {
                    try {
                        return PendingInvoiceDto.builder()
                            .idSolicitudActos(((Number)row[0]).longValue())
                            .factEstadoFactura((String) row[1])
                            .provincia((String) row[2])
                            .build();
                    } catch (Exception e) {
                        log.error("Error mapeando factura: {}", Arrays.toString(row));
                        log.debug("Detalles:", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Procesadas {}/{} facturas en {} ms", dtos.size(), results.size(), duration);
            
            if (dtos.isEmpty()) {
                log.warn("No se encontraron facturas pendientes");
            } else if (log.isDebugEnabled()) {
                log.debug("Primera factura - ID: {}, Provincia: {}", 
                    dtos.get(0).getIdSolicitudActos(), 
                    dtos.get(0).getProvincia());
            }
            
            return dtos;
            
        } catch (DataAccessException dae) {
            log.error("Error de base de datos: {}", dae.getMessage());
            throw dae;
            
        } catch (Exception e) {
            log.error("Error procesando facturas: {}", e.getMessage());
            log.debug("Detalles:", e);
            throw new DataAccessException("Error al procesar facturas", e) {};
        }
    }
}
