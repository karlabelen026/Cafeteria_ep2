package cl.duoc.cafeteria.notificaciones.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Mensaje del chat interno del equipo (ver docs/EP2_PLAN.md seccion 6.2):
 * cualquier staff logueado puede escribir, todos los roles lo ven. No es la
 * "Mensajeria" de administracion de RabbitMQ (esa es otra pantalla, solo
 * ADMIN) — este es un canal unico de texto entre el equipo.
 */
@Entity
@Table(name = "mensajes_chat")
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 80)
    private String autor;

    @NotBlank
    @Size(max = 30)
    private String rol;

    @NotBlank
    @Size(max = 500)
    private String texto;

    private Instant creadoEn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
