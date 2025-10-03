package factura.flow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entidad que representa la tabla FacturasElectronicas en el esquema NOTARIAL_JAVA.Pesnot.
 */
@Entity
@Table(name = "FacturasElectronicas", schema = "Pesnot")
@Data
public class FacturasElectronicas {
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "idSolicitudActos")
    private Long idSolicitudActos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idSolicitudActos", referencedColumnName = "id", insertable = false, updatable = false)
    private SolicitudActos solicitudActos;
    
    @Column(name = "idEscritura")
    private Long idEscritura;
    
    @Column(name = "factTipo", length = 50)
    private String factTipo;
    
    @Column(name = "factEstadoFactura", length = 50)
    private String factEstadoFactura;
    
    @Column(name = "factEnviado")
    private Boolean factEnviado;
    
    @Column(name = "factAutorizado")
    private Boolean factAutorizado;
    
    @Column(name = "factEjecutaSri")
    private Boolean factEjecutaSri;
    
    @Column(name = "factInicioAutorizacion")
    private LocalDateTime factInicioAutorizacion;
    
    @Column(name = "factFinAutorizacion")
    private LocalDateTime factFinAutorizacion;
    
    @Column(name = "estado", length = 50)
    private String estado;
    
    @Column(name = "fechaCrea")
    private LocalDateTime fechaCrea;
    
    @Column(name = "fechaModifica")
    private LocalDateTime fechaModifica;
    
    @Column(name = "idPersonaCrea")
    private Long idPersonaCrea;
    
    @Column(name = "idPersonaModifica")
    private Long idPersonaModifica;
    
    @Column(name = "ipCrea", length = 50)
    private String ipCrea;
    
    @Column(name = "equipoCrea", length = 50)
    private String equipoCrea;
    
    @Column(name = "ipModifica", length = 50)
    private String ipModifica;
    
    @Column(name = "equipoModifica", length = 50)
    private String equipoModifica;
    
    @Column(name = "motivoModifica", length = 500)
    private String motivoModifica;
}
