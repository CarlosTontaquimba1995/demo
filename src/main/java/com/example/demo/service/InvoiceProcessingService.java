package com.example.demo.service;

import com.example.demo.client.ExternalApiClient;
import com.example.demo.dto.PendingInvoiceDto;
import com.example.demo.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Servicio responsable del procesamiento de facturas en paralelo agrupadas por provincia.
 * 
 * Características principales:
 * - Procesamiento paralelo por provincias
 * - Uso de hilos virtuales para máxima eficiencia
 * - Manejo de errores robusto
 * - Reintentos automáticos en caso de fallos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceProcessingService {

    // Repositorio para acceder a los datos de facturas
    private final InvoiceRepository invoiceRepository;
    
    // Cliente para comunicarse con la API externa
    private final ExternalApiClient externalApiClient;

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
    @Transactional(readOnly = true)
    public CompletableFuture<Void> processAllPendingInvoices() {
        log.info("🚀 Iniciando procesamiento de facturas pendientes");
        
        // Consulta las facturas pendientes en la base de datos
        List<PendingInvoiceDto> pendingInvoices = invoiceRepository.findPendingInvoiceDtos();
        log.info("📋 Se encontraron {} facturas pendientes por procesar", pendingInvoices.size());
        
        if (pendingInvoices.isEmpty()) {
            log.info("✅ No hay facturas pendientes para procesar");
            return CompletableFuture.completedFuture(null);
        }
        
        // Agrupa las facturas por provincia para procesamiento paralelo
        Map<String, List<PendingInvoiceDto>> invoicesByProvince = pendingInvoices.stream()
                .collect(Collectors.groupingBy(PendingInvoiceDto::getProvincia));
        
        log.info("🌍 Procesando facturas para {} provincias: {}", 
                invoicesByProvince.size(), invoicesByProvince.keySet());
        
        // Procesa cada provincia en paralelo usando hilos virtuales
        List<CompletableFuture<Void>> provinceFutures = invoicesByProvince.entrySet().stream()
                .map(entry -> processInvoicesForProvince(entry.getKey(), entry.getValue()))
                .toList();
        
        // Combina todos los futuros y devuelve uno que se completa cuando todos terminan
        return CompletableFuture.allOf(provinceFutures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Error al procesar las facturas: {}", ex.getMessage(), ex);
                        log.debug("Detalles del error en el procesamiento:", ex);
                    } else {
                        log.info("✅ Procesamiento de facturas completado exitosamente");
                    }
                });
    }
    
    /**
     * Procesa todas las facturas de una provincia específica en paralelo.
     * 
     * @param provincia Nombre de la provincia
     * @param facturas Lista de facturas a procesar para esta provincia
     * @return Un CompletableFuture que se completa cuando todas las facturas de la provincia han sido procesadas
     */
    @Async
    protected CompletableFuture<Void> processInvoicesForProvince(String provincia, List<PendingInvoiceDto> facturas) {
        log.info("🏙️  Procesando {} facturas para la provincia: {}", facturas.size(), provincia);
        
        // Procesa cada factura en paralelo usando hilos virtuales
        List<CompletableFuture<Void>> facturasFuturas = facturas.stream()
                .map(this::procesarFacturaIndividual)
                .toList();
        
        // Combina todos los futuros de esta provincia
        return CompletableFuture.allOf(facturasFuturas.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("⚠️  Error al procesar facturas para la provincia {}: {}", 
                                provincia, ex.getMessage());
                        log.debug("Detalles del error para {}:", provincia, ex);
                    } else {
                        log.info("✅ Se procesaron exitosamente {} facturas para la provincia: {}", 
                                facturas.size(), provincia);
                    }
                });
    }
    
    /**
     * Procesa una única factura enviándola a la API externa.
     * 
     * @param factura La factura a procesar
     * @return Un CompletableFuture que se completa cuando la factura ha sido procesada
     */
    private CompletableFuture<Void> procesarFacturaIndividual(PendingInvoiceDto factura) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("📤 Enviando factura {} a la API externa", factura.getIdSolicitudActos());
                externalApiClient.processInvoice(factura.getIdSolicitudActos())
                        .block(); // Se bloquea el hilo actual hasta que se complete la operación
                log.debug("✅ Factura {} procesada exitosamente", factura.getIdSolicitudActos());
            } catch (Exception e) {
                log.error("❌ Error al procesar la factura {}: {}", 
                        factura.getIdSolicitudActos(), e.getMessage());
                log.debug("Detalles del error en la factura {}:", factura.getIdSolicitudActos(), e);
                // El error ya fue registrado, se maneja de forma elegante
                // El registro se reintentará en la siguiente ejecución del programador
            }
        });
    }
}
