package factura.flow.service;

import factura.flow.dto.PendingInvoiceDto;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interfaz que define el contrato para el servicio de procesamiento de
 * facturas.
 * 
 * Características principales:
 * - Procesamiento paralelo por provincias
 * - Uso de hilos virtuales para máxima eficiencia
 * - Manejo de errores robusto
 * - Reintentos automáticos en caso de fallos
 */
public interface InvoiceProcessingService {

    /**
     * Procesa todas las facturas pendientes, agrupándolas por provincia y procesando cada grupo en paralelo.
     * 
     * @return Un CompletableFuture que se completa cuando todas las facturas han sido procesadas
     * 
     * Flujo de ejecución:
     * 1. Consulta las facturas pendientes en la base de datos
     * 2. Agrupa las facturas por provincia
     * 3. Procesa cada grupo de provincias en paralelo
     * 4. Devuelve un futuro que se completa cuando terminan todos los procesos
     */
    CompletableFuture<Void> processAllPendingInvoices();
    
    /**
     * Procesa todas las facturas de una provincia específica en paralelo.
     * 
     * @param provincia Nombre de la provincia
     * @param facturas Lista de facturas a procesar para esta provincia
     * @return Un CompletableFuture que se completa cuando todas las facturas de la provincia han sido procesadas
     */
    CompletableFuture<Void> processInvoicesForProvince(String provincia, List<PendingInvoiceDto> facturas);
    
    /**
     * Procesa una factura individual de manera asíncrona.
     * 
     * @param factura DTO con la información de la factura a procesar
     * @return Un CompletableFuture que se completa cuando la factura ha sido
     *         procesada
     */
    CompletableFuture<Void> procesarFacturaIndividual(PendingInvoiceDto factura);
}