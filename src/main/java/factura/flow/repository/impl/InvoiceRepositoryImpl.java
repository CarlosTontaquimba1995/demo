package factura.flow.repository.impl;

import factura.flow.dto.PendingInvoiceDto;
import factura.flow.repository.InvoiceRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final String LAST_PROCESSED_DATE_KEY = "ultima_fecha_procesada";
    private static final String DEFAULT_START_DATE = "2025-01-01 16:00:00.000";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public LocalDateTime getLastProcessedDate() {
        try {
            String lastDateStr = redisTemplate.opsForValue().get(LAST_PROCESSED_DATE_KEY);
            log.debug("Valor obtenido de Redis para {}: {}", LAST_PROCESSED_DATE_KEY, lastDateStr);

            if (lastDateStr == null || lastDateStr.trim().isEmpty()) {
                throw new IllegalStateException("No se encontró fecha en Redis");
            }

            return LocalDateTime.parse(lastDateStr, DATE_TIME_FORMATTER);

        } catch (Exception e) {
            log.error("Error al obtener/parsear la fecha de Redis: {}", e.getMessage());
            // Si hay algún error, forzamos la inicialización
            initializeDefaultDate();
            return LocalDateTime.parse(DEFAULT_START_DATE, DATE_TIME_FORMATTER);
        }
    }

    @Override
    public void setLastProcessedDate(LocalDateTime date) {
        try {
            String dateStr = date.format(DATE_TIME_FORMATTER);
            redisTemplate.opsForValue().set(LAST_PROCESSED_DATE_KEY, dateStr);
            log.debug("Fecha guardada en Redis ({}): {}", LAST_PROCESSED_DATE_KEY, dateStr);
        } catch (Exception e) {
            log.error("Error al guardar la fecha en Redis", e);
            throw new RuntimeException("No se pudo guardar la fecha en Redis", e);
        }
    }

    private void initializeDefaultDate() {
        try {
            log.info("Inicializando fecha de procesamiento con valor por defecto: {}", DEFAULT_START_DATE);
            setLastProcessedDate(LocalDateTime.parse(DEFAULT_START_DATE, DATE_TIME_FORMATTER));
        } catch (Exception e) {
            log.error("Error al inicializar la fecha por defecto", e);
            throw new RuntimeException("No se pudo inicializar la fecha por defecto", e);
        }
    }
    
    private static final String PENDING_INVOICES_QUERY = """
        SELECT 
            fe.idSolicitudActos as idSolicitudActos, 
            fe.factEstadoFactura as factEstadoFactura, 
                                                                    n.provincia as provincia,
                                                                    fe.fechaCrea as fechaCrea
        FROM [NOTARIAL_JAVA].[Pesnot].[FacturasElectronicas] fe
        INNER JOIN [NOTARIAL_JAVA].[Pesnot].[SolicitudActos] sa ON fe.idSolicitudActos = sa.id
        INNER JOIN [PESNOT].[UBI].[Notarias] n ON n.idNotaria = sa.idNotaria
                                                                WHERE (fe.factEstadoFactura IN ('NO_FIRMADO','NO_WS1','NO_WS2','NO_ZIP'))
                                                                AND fe.fechaCrea > ?
    """;

    /**
     * Ejecuta la consulta SQL para obtener las facturas pendientes.
     * @return Lista de arreglos de objetos con los resultados de la consulta
     */
    private List<Object[]> findPendingInvoices() {
        log.debug("Consultando facturas pendientes...");
        try {
            LocalDateTime lastProcessedDate = getLastProcessedDate();
            log.info("Buscando facturas posteriores a: {}", lastProcessedDate);

            List<Object[]> results = jdbcTemplate.query(
                    PENDING_INVOICES_QUERY,
                    preparedStatement -> preparedStatement.setTimestamp(1,
                            java.sql.Timestamp.valueOf(lastProcessedDate)),
                    (rs, rowNum) -> new Object[] {
                    rs.getLong("idSolicitudActos"),
                    rs.getString("factEstadoFactura"),
                            rs.getString("provincia"),
                            rs.getTimestamp("fechaCrea").toLocalDateTime()
                }
            );

            log.debug("Consulta completada. Facturas encontradas: {}", results.size());

            // Update last processed date to the latest invoice's date
            if (!results.isEmpty()) {
                LocalDateTime latestDate = (LocalDateTime) results.get(results.size() - 1)[3];
                setLastProcessedDate(latestDate);
                log.info("Actualizada última fecha procesada a: {}", latestDate);
            }

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
