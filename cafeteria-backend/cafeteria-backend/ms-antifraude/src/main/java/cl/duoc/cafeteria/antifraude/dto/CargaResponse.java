package cl.duoc.cafeteria.antifraude.dto;

/** Resultado de la prueba de carga (POST /api/antifraude/carga). */
public record CargaResponse(int cantidad, long milisegundos, long mensajesPorSegundo) {
}
