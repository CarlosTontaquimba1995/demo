package factura.flow.service.impl;

import factura.flow.client.ExternalApiClient;
import factura.flow.dto.PendingInvoiceDto;
import factura.flow.repository.InvoiceRepository;
import factura.flow.service.InvoiceProcessingService;
import factura.flow.service.kafka.InvoiceProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
@Service
public class InvoiceProcessingServiceImpl implements InvoiceProcessingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProcessingServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final ExternalApiClient externalApiClient;
    private final InvoiceProducer invoiceProducer;

    public InvoiceProcessingServiceImpl(InvoiceRepository invoiceRepository,
            ExternalApiClient externalApiClient,
            InvoiceProducer invoiceProducer) {
        this.invoiceRepository = invoiceRepository;
        this.externalApiClient = externalApiClient;
        this.invoiceProducer = invoiceProducer;
    }

    @Override
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
        log.debug("📤 Encolando factura ID: {} para procesamiento", factura.getIdSolicitudActos());

        // Enviar la factura a Kafka para su procesamiento asíncrono
        invoiceProducer.sendInvoiceForProcessing(factura);

        // Retornar un futuro completado ya que el procesamiento real será manejado por
        // el consumidor de Kafka
        return CompletableFuture.completedFuture(null);
    }
}
