package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "Notarias", schema = "UBI")
@Data
public class Notaria {
    @Id
    @Column(name = "id")
    private Long id;
    
    @Column(name = "idNotaria")
    private Long idNotaria;
    
    @Column(name = "estado")
    private String estado;
    
    @Column(name = "idPersonaCrea")
    private Long idPersonaCrea;
    
    @Column(name = "idPersonaModifica")
    private Long idPersonaModifica;
    
    @Column(name = "fechaCrea")
    private LocalDateTime fechaCrea;
    
    @Column(name = "fechaModifica")
    private LocalDateTime fechaModifica;
    
    @Column(name = "ipCrea")
    private String ipCrea;
    
    @Column(name = "ipModifica")
    private String ipModifica;
    
    @Column(name = "equipoCrea")
    private String equipoCrea;
    
    @Column(name = "equipoModifica")
    private String equipoModifica;
    
    @Column(name = "apellidos")
    private String apellidos;
    
    @Column(name = "canton")
    private String canton;
    
    @Column(name = "descripcion")
    private String descripcion;
    
    @Column(name = "direccion")
    private String direccion;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "idAcronimoNotaria")
    private Long idAcronimoNotaria;
    
    @Column(name = "latitudNotaria")
    private String latitudNotaria;
    
    @Column(name = "longitudNotaria")
    private String longitudNotaria;
    
    @Column(name = "motivoModifica")
    private String motivoModifica;
    
    @Column(name = "nombres")
    private String nombres;
    
    @Column(name = "provincia")
    private String provincia;
    
    @Column(name = "telefono")
    private String telefono;
    
    @Column(name = "url")
    private String url;
    
    @Column(name = "cedulaNotario")
    private String cedulaNotario;
}
