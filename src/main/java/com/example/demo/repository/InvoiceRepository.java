package com.example.demo.repository;

import com.example.demo.entity.FacturasElectronicas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repositorio para el acceso a datos de facturas electrónicas.
 * Proporciona métodos para consultar y gestionar facturas pendientes de
 * procesar.
 * 
 * La implementación personalizada se encuentra en
 * {@link InvoiceRepositoryCustom}.
 */
public interface InvoiceRepository extends JpaRepository<FacturasElectronicas, Long>, InvoiceRepositoryCustom {
    
    /**
     * Recupera todas las facturas pendientes de procesar.
     * Consulta las facturas que no han sido marcadas como 'COMPLETADO' o cuyo
     * estado es nulo.
     * 
     * @return Lista de arreglos de objetos con los resultados de la consulta
     * @throws org.springframework.dao.DataAccessException si ocurre un error al
     *                                                     acceder a los datos
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
}
