package com.example.demo.scheduler;

import com.example.demo.service.InvoiceProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that triggers the processing of pending invoices at regular intervals.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProcessingScheduler {

    private final InvoiceProcessingService invoiceProcessingService;

    /**
     * Scheduler that runs every 5 seconds to process pending invoices.
     * The method is executed on a virtual thread, allowing for efficient concurrency.
     */
    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    public void processPendingInvoices() {
        log.debug("Scheduled task started: Processing pending invoices");
        
        try {
            // Process all pending invoices
            invoiceProcessingService.processAllPendingInvoices()
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Error in scheduled invoice processing", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Unexpected error in invoice processing scheduler", e);
        }
    }
}
