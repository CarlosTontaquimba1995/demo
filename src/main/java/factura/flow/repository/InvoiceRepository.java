package factura.flow.repository;

import factura.flow.entity.FacturasElectronicas;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * Repositorio para el acceso a datos de facturas electrónicas.
 * Proporciona métodos para consultar y gestionar facturas pendientes de
 * procesar.
 * 
 * La implementación personalizada se encuentra en
 * {@link InvoiceRepositoryCustom}.
 */
public interface InvoiceRepository extends JpaRepository<FacturasElectronicas, Long>, InvoiceRepositoryCustom {
}
