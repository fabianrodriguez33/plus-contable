package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documentos_legales", schema = "tributario_schema")
@Getter
@Setter
public class DocumentoLegal {
    public enum Tipo { MINUTA, COPIA_LITERAL, VIGENCIA_PODERES, FICHA_RUC }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "ruta_almacenamiento", nullable = false, length = 400)
    private String rutaAlmacenamiento;

    @Column(name = "subido_en", nullable = false)
    private LocalDateTime subidoEn = LocalDateTime.now();
}
