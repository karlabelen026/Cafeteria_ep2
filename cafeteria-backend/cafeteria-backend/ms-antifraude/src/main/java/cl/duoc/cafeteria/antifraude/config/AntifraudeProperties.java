package cl.duoc.cafeteria.antifraude.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea el bloque app.antifraude de application.yml: los umbrales y la tasa
 * de cambio que usa la topologia (streams/AntifraudeTopology), y los nombres
 * de sus state stores. Cambiar una regla es cambiar el yml, no el codigo.
 */
@ConfigurationProperties(prefix = "app.antifraude")
public class AntifraudeProperties {

    /** Montos menores (CLP) se descartan como ruido. */
    private double montoMinimo = 10;
    /** CLP por 1 USD (tasa simulada para el enriquecimiento). */
    private double tasaClpUsd = 950;
    /** Monto (CLP) sobre el que una transaccion genera alerta ALTA. */
    private double umbralAlto = 50000;
    /** Mas transacciones que esto en la ventana genera la alerta "Rafaga de pagos". */
    private int maxTransaccionesVentana = 5;
    private int ventanaMinutos = 5;
    private String storeTotales = "total-por-cliente";
    private String storeRafagas = "pagos-por-ventana";
    /** Cuantas alertas recientes guarda en memoria la vista GET /alertas. */
    private int maxAlertasMemoria = 200;

    public double getMontoMinimo() {
        return montoMinimo;
    }

    public void setMontoMinimo(double montoMinimo) {
        this.montoMinimo = montoMinimo;
    }

    public double getTasaClpUsd() {
        return tasaClpUsd;
    }

    public void setTasaClpUsd(double tasaClpUsd) {
        this.tasaClpUsd = tasaClpUsd;
    }

    public double getUmbralAlto() {
        return umbralAlto;
    }

    public void setUmbralAlto(double umbralAlto) {
        this.umbralAlto = umbralAlto;
    }

    public int getMaxTransaccionesVentana() {
        return maxTransaccionesVentana;
    }

    public void setMaxTransaccionesVentana(int maxTransaccionesVentana) {
        this.maxTransaccionesVentana = maxTransaccionesVentana;
    }

    public int getVentanaMinutos() {
        return ventanaMinutos;
    }

    public void setVentanaMinutos(int ventanaMinutos) {
        this.ventanaMinutos = ventanaMinutos;
    }

    public String getStoreTotales() {
        return storeTotales;
    }

    public void setStoreTotales(String storeTotales) {
        this.storeTotales = storeTotales;
    }

    public String getStoreRafagas() {
        return storeRafagas;
    }

    public void setStoreRafagas(String storeRafagas) {
        this.storeRafagas = storeRafagas;
    }

    public int getMaxAlertasMemoria() {
        return maxAlertasMemoria;
    }

    public void setMaxAlertasMemoria(int maxAlertasMemoria) {
        this.maxAlertasMemoria = maxAlertasMemoria;
    }
}
