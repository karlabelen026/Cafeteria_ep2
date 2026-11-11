package cl.duoc.cafeteria.reportes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoria de cada transaccion de pago leida desde Kafka (topic
 * cafeteria.pagos.transacciones). La PK es el eventId del evento, asi que
 * tambien sirve de control de idempotencia: un evento repetido no se guarda dos
 * veces. Guarda de donde se leyo (particion y offset) para poder rastrearlo.
 */
@Entity
@Table(name = "auditoria_transacciones", indexes = {
        @Index(name = "idx_auditoria_ocurrido_en", columnList = "ocurridoEn"),
        @Index(name = "idx_auditoria_cliente", columnList = "clienteEmail")
})
public class AuditoriaTransaccion {

    @Id
    private UUID eventId;

    private Instant ocurridoEn;
    private int version;
    private Long pedidoId;
    private Long pagoId;
    private String clienteEmail;
    private double monto;
    private String metodoPago;
    private boolean aprobado;

    private Instant procesadoEn;
    private int particion;

    // OFFSET es palabra reservada en H2 y MySQL: la columna lleva otro nombre.
    @Column(name = "offset_kafka")
    private long offsetKafka;

    public AuditoriaTransaccion() {
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public Instant getOcurridoEn() { return ocurridoEn; }
    public void setOcurridoEn(Instant ocurridoEn) { this.ocurridoEn = ocurridoEn; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public Long getPagoId() { return pagoId; }
    public void setPagoId(Long pagoId) { this.pagoId = pagoId; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public boolean isAprobado() { return aprobado; }
    public void setAprobado(boolean aprobado) { this.aprobado = aprobado; }
    public Instant getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(Instant procesadoEn) { this.procesadoEn = procesadoEn; }
    public int getParticion() { return particion; }
    public void setParticion(int particion) { this.particion = particion; }
    public long getOffsetKafka() { return offsetKafka; }
    public void setOffsetKafka(long offsetKafka) { this.offsetKafka = offsetKafka; }
}
