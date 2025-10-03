package factura.flow.service.impl;

import factura.flow.client.ExternalApiClient;
import factura.flow.dto.PendingInvoiceDto;
import factura.flow.repository.InvoiceRepository;
import factura.flow.service.InvoiceProcessingService;
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
 * Implementación del servicio de procesamiento de facturas.
 * 
 * Esta implementación procesa facturas en paralelo agrupadas por provincia
 * utilizando hilos virtuales para máxima eficiencia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceProcessingServiceImpl implements InvoiceProcessingService {

    private final InvoiceRepository invoiceRepository;
    private final ExternalApiClient externalApiClient;

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Void> processAllPendingInvoices() {
        log.info("🚀 Iniciando procesamiento de facturas pendientes");
        
        List<PendingInvoiceDto> pendingInvoices = invoiceRepository.findPendingInvoiceDtos();
        log.info("📋 Se encontraron {} facturas pendientes por procesar", pendingInvoices.size());
        
        if (pendingInvoices.isEmpty()) {
            log.info("✅ No hay facturas pendientes para procesar");
            return CompletableFuture.completedFuture(null);
        }
        
        Map<String, List<PendingInvoiceDto>> invoicesByProvince = pendingInvoices.stream()
                .collect(Collectors.groupingBy(PendingInvoiceDto::getProvincia));
        
        log.info("🌍 Procesando facturas para {} provincias: {}", 
                invoicesByProvince.size(), invoicesByProvince.keySet());
        
        List<CompletableFuture<Void>> provinceFutures = invoicesByProvince.entrySet().stream()
                .map(entry -> processInvoicesForProvince(entry.getKey(), entry.getValue()))
                .toList();
        
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
    
    @Override
    @Async
    public CompletableFuture<Void> processInvoicesForProvince(String provincia, List<PendingInvoiceDto> facturas) {
        log.info("🏙️  Procesando {} facturas para la provincia: {}", facturas.size(), provincia);
        
        List<CompletableFuture<Void>> facturasFuturas = facturas.stream()
                .map(this::procesarFacturaIndividual)
                .toList();
        
        return CompletableFuture.allOf(facturasFuturas.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Error al procesar facturas para la provincia {}: {}", provincia, ex.getMessage());
                    } else {
                        log.info("✅ Procesamiento completado para la provincia: {} - {} facturas procesadas", 
                                provincia, facturas.size());
                    }
                });
    }
    
    @Override
    @Async
    public CompletableFuture<Void> procesarFacturaIndividual(PendingInvoiceDto factura) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("📝 Procesando factura ID: {}", factura.getIdSolicitudActos());
                
                // Simulamos un procesamiento con un pequeño retraso
                Thread.sleep(100);
                
                // Llamada al servicio externo para procesar la factura
                externalApiClient.processInvoice(factura.getIdSolicitudActos())
                    .block(); // Bloqueamos ya que estamos en un contexto asíncrono manejado por CompletableFuture
                
                log.debug("✅ Factura {} procesada exitosamente", factura.getIdSolicitudActos());
            } catch (Exception e) {
                log.error("❌ Error al procesar la factura {}: {}", factura.getIdSolicitudActos(), e.getMessage());
                throw new RuntimeException("Error al procesar la factura: " + factura.getIdSolicitudActos(), e);
            }
        });
    }
}
