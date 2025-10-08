package factura.flow.scheduler;

import factura.flow.service.InvoiceProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programador que inicia el procesamiento de facturas pendientes en intervalos
 * regulares.
 * Utiliza un bloqueo de procesamiento para evitar la ejecución concurrente del
 * mismo lote.
 * 
 * Características principales:
 * - Ejecución programada cada 5 segundos
 * - Prevención de procesamiento duplicado
 * - Manejo seguro de hilos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProcessingScheduler {

    // Servicio que contiene la lógica de procesamiento de facturas
    private final InvoiceProcessingService invoiceProcessingService;

    // Bandera atómica para controlar el bloqueo de procesamiento
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    /**
     * Método programado que se ejecuta cada 5 segundos para procesar facturas
     * pendientes.
     * Implementa un mecanismo de bloqueo para evitar el procesamiento concurrente.
     * 
     * Flujo de ejecución:
     * 1. Verifica si ya hay un proceso en ejecución
     * 2. Si no hay proceso activo, inicia el procesamiento
     * 3. Al finalizar, libera el bloqueo independientemente del resultado
     */
    @Scheduled(fixedRate = 60000) // Se ejecuta cada 5 segundos (60000 cada minuto)
    public void processPendingInvoices() {
        // Verifica y adquiere el bloqueo de procesamiento de forma atómica
        if (!isProcessing.compareAndSet(false, true)) {
            log.debug("Se omite ejecución: Ya hay un lote de facturas en proceso");
            return;
        }

        log.info("Iniciando proceso de facturas pendientes");
        
        try {
            // Procesa todas las facturas pendientes
            invoiceProcessingService.processAllPendingInvoices()
                    .whenComplete((result, ex) -> {
                        // Siempre libera el bloqueo de procesamiento
                        isProcessing.set(false);

                        if (ex != null) {
                            log.error("❌ Error durante el procesamiento de facturas: {}", ex.getMessage(), ex);
                            log.debug("Detalles del error en el procesamiento de facturas:", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("❌ Error inesperado en el programador de facturas: {}", e.getMessage(), e);
            log.debug("Detalles del error inesperado:", e);
        }
    }
}
