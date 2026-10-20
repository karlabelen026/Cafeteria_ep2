package cl.duoc.cafeteria.notificaciones.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Alerta para el panel del dashboard (ver docs/EP2_PLAN.md seccion 5 y 6.2):
 * stock bajo, pedido listo para entregar, o un mensaje que termino en DLQ.
 */
@Entity
@Table(name = "alertas")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(regexp = "STOCK_BAJO|PEDIDO_LISTO|DLQ|FRAUDE", message = "Tipo de alerta invalido")
    private String tipo;

    @NotBlank(message = "El mensaje de la alerta no puede estar vacio")
    private String mensaje;

    private boolean leida;
    private Instant fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
}
