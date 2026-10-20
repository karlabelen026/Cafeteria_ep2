package cl.duoc.cafeteria.notificaciones.service;

/**
 * Reinyecta en alertas-fraude los mensajes que quedaron en su DLT (p.ej.
 * despues de corregir la causa del error), para que se procesen de nuevo.
 * No conoce Kafka: la implementacion vive en messaging/dlt.
 */
public interface ReinyeccionDltService {

    /** @return cuantos mensajes se reinyectaron (como maximo "maximo"). */
    int reinyectar(int maximo);
}
