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
 * Service responsible for processing invoices in parallel by province.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceProcessingService {

    private final InvoiceRepository invoiceRepository;
    private final ExternalApiClient externalApiClient;

    /**
     * Processes all pending invoices, grouping them by province and processing each group in parallel.
     * 
     * @return A CompletableFuture that completes when all invoices have been processed
     */
    @Transactional(readOnly = true)
    public CompletableFuture<Void> processAllPendingInvoices() {
        log.info("Starting to process all pending invoices");
        
        // Fetch all pending invoices from the database
        List<PendingInvoiceDto> pendingInvoices = invoiceRepository.findPendingInvoiceDtos();
        log.info("Found {} pending invoices to process", pendingInvoices.size());
        
        if (pendingInvoices.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Group invoices by province
        Map<String, List<PendingInvoiceDto>> invoicesByProvince = pendingInvoices.stream()
                .collect(Collectors.groupingBy(PendingInvoiceDto::getProvincia));
        
        log.info("Processing invoices for {} provinces: {}", 
                invoicesByProvince.size(), invoicesByProvince.keySet());
        
        // Process each province's invoices in parallel using virtual threads
        List<CompletableFuture<Void>> provinceFutures = invoicesByProvince.entrySet().stream()
                .map(entry -> processInvoicesForProvince(entry.getKey(), entry.getValue()))
                .toList();
        
        // Combine all futures and return a single future that completes when all are done
        return CompletableFuture.allOf(provinceFutures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error processing invoices", ex);
                    } else {
                        log.info("Finished processing all pending invoices");
                    }
                });
    }
    
    /**
     * Processes all invoices for a specific province in parallel.
     * 
     * @param province The name of the province
     * @param invoices The list of invoices to process for this province
     * @return A CompletableFuture that completes when all invoices for the province have been processed
     */
    @Async
    protected CompletableFuture<Void> processInvoicesForProvince(String province, List<PendingInvoiceDto> invoices) {
        log.info("Processing {} invoices for province: {}", invoices.size(), province);
        
        // Process each invoice in parallel using virtual threads
        List<CompletableFuture<Void>> invoiceFutures = invoices.stream()
                .map(this::processSingleInvoice)
                .toList();
        
        // Combine all futures for this province
        return CompletableFuture.allOf(invoiceFutures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error processing invoices for province: " + province, ex);
                    } else {
                        log.info("Finished processing {} invoices for province: {}", 
                                invoices.size(), province);
                    }
                });
    }
    
    /**
     * Processes a single invoice by sending it to the external API.
     * 
     * @param invoice The invoice to process
     * @return A CompletableFuture that completes when the invoice has been processed
     */
    private CompletableFuture<Void> processSingleInvoice(PendingInvoiceDto invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                externalApiClient.processInvoice(invoice.getIdSolicitudActos())
                        .block(); // Blocking is OK here as we're in a virtual thread
            } catch (Exception e) {
                log.error("Error processing invoice {}: {}", 
                        invoice.getIdSolicitudActos(), e.getMessage());
                // The error is already logged by the client, we just need to handle it gracefully
                // The record will be retried in the next scheduler run since we don't update the status
            }
        });
    }
}
