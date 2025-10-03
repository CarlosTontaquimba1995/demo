package factura.flow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity representing the SolicitudActos table in the NOTARIAL_JAVA.Pesnot schema.
 */
@Entity
@Table(name = "SolicitudActos", schema = "Pesnot")
@Data
public class SolicitudActos {
    @Id
    @Column(name = "id")
    private Long id;
    
    @Column(name = "idNotaria")
    private Long idNotaria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idNotaria", referencedColumnName = "idNotaria", insertable = false, updatable = false)
    private Notaria notaria;
    
    @Column(name = "idActoNotarial")
    private Long idActoNotarial;
    
    @Column(name = "idCatalogoTipoAgenda")
    private Long idCatalogoTipoAgenda;
    
    @Column(name = "idFuncionario")
    private Long idFuncionario;
    
    @Column(name = "fechaInicial")
    private LocalDateTime fechaInicial;
    
    @Column(name = "fechaFinal")
    private LocalDateTime fechaFinal;
    
    @Column(name = "idCatalogoEstadoAgenda")
    private Long idCatalogoEstadoAgenda;
    
    @Column(name = "observacionesCliente", columnDefinition = "TEXT")
    private String observacionesCliente;
    
    @Column(name = "observacionesNotaria", columnDefinition = "TEXT")
    private String observacionesNotaria;
    
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
    
    @Column(name = "matrizArchivoCargado", columnDefinition = "TEXT")
    private String matrizArchivoCargado;
    
    @Column(name = "fechaInicialHistorico")
    private LocalDateTime fechaInicialHistorico;
    
    @Column(name = "fechaFinalHistorico")
    private LocalDateTime fechaFinalHistorico;
    
    @Column(name = "extractoArchivoCargado", columnDefinition = "TEXT")
    private String extractoArchivoCargado;
    
    @Column(name = "numeroIntervinientesSolicitados")
    private Integer numeroIntervinientesSolicitados;
    
    @Column(name = "razonArchivoCargado", columnDefinition = "TEXT")
    private String razonArchivoCargado;
    
    @Column(name = "idSolicitudActosPadre")
    private Long idSolicitudActosPadre;
    
    @Column(name = "numeroJuegosSolicitados")
    private Integer numeroJuegosSolicitados;
    
    @Column(name = "idTipoActoNotarial")
    private Long idTipoActoNotarial;
    
    @Column(name = "estadoCobrado", length = 50)
    private String estadoCobrado;
    
    @Column(name = "otrosArchivoCargado", columnDefinition = "TEXT")
    private String otrosArchivoCargado;
    
    @Column(name = "cantidadCopiasDescargadas")
    private Integer cantidadCopiasDescargadas;
}
