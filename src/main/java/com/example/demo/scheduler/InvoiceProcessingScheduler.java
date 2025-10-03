package com.example.demo.scheduler;

import com.example.demo.service.InvoiceProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler that triggers the processing of pending invoices at regular
 * intervals.
 * Uses a processing lock to prevent concurrent execution of the same batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProcessingScheduler {

    private final InvoiceProcessingService invoiceProcessingService;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    /**
     * Scheduler that runs every 5 seconds to process pending invoices.
     * The method prevents concurrent execution to avoid duplicate processing.
     */
    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    public void processPendingInvoices() {
        // Skip if processing is already in progress
        if (!isProcessing.compareAndSet(false, true)) {
            log.debug("Skipping: Another invoice processing batch is already running");
            return;
        }

        log.debug("Starting invoice processing batch");
        
        try {
            // Process all pending invoices
            invoiceProcessingService.processAllPendingInvoices()
                    .whenComplete((result, ex) -> {
                        // Always release the processing lock
                        isProcessing.set(false);

                        if (ex != null) {
                            log.error("Error in scheduled invoice processing", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Unexpected error in invoice processing scheduler", e);
        }
    }
}
