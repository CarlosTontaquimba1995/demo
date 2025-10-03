package com.example.demo.repository;

import com.example.demo.dto.PendingInvoiceDto;

import java.util.List;

/**
 * Interfaz personalizada para operaciones personalizadas de repositorio de facturas.
 * Las implementaciones de esta interfaz proporcionan funcionalidad adicional
 * más allá de las operaciones CRUD estándar.
 */
public interface InvoiceRepositoryCustom {
    
    /**
     * Mapea los resultados de la consulta a objetos PendingInvoiceDto.
     * Incluye registro de métricas y manejo de errores.
     * 
     * @return Lista de DTOs de facturas pendientes
     * @throws org.springframework.dao.DataAccessException si ocurre un error al procesar los resultados
     */
    List<PendingInvoiceDto> findPendingInvoiceDtos();
}
