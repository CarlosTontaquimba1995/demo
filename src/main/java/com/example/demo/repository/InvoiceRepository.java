package com.example.demo.repository;

import com.example.demo.dto.PendingInvoiceDto;
import com.example.demo.entity.FacturasElectronicas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for accessing invoice data from the database.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<FacturasElectronicas, Long> {
    
    /**
     * Fetches all pending invoices that need to be processed.
     * @return List of Object arrays containing the query results
     */
    @Query(nativeQuery = true, value = """
        SELECT 
            fe.idSolicitudActos as idSolicitudActos, 
            fe.factEstadoFactura as factEstadoFactura, 
            n.provincia as provincia
        FROM [NOTARIAL_JAVA].[Pesnot].[FacturasElectronicas] fe
        INNER JOIN [NOTARIAL_JAVA].[Pesnot].[SolicitudActos] sa ON fe.idSolicitudActos = sa.id
        INNER JOIN [PESNOT].[UBI].[Notarias] n ON n.idNotaria = sa.idNotaria
        WHERE fe.factEstadoFactura IS NULL OR fe.factEstadoFactura <> 'COMPLETADO'""")
    List<Object[]> findPendingInvoices();
    
    /**
     * Maps the raw query results to PendingInvoiceDto objects
     */
    default List<PendingInvoiceDto> findPendingInvoiceDtos() {
        return findPendingInvoices().stream()
            .map(row -> PendingInvoiceDto.builder()
                .idSolicitudActos(((Number)row[0]).longValue())
                .factEstadoFactura((String) row[1])
                .provincia((String) row[2])
                .build())
            .toList();
    }
}
