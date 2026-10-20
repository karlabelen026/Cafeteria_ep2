package cl.duoc.cafeteria.antifraude.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Transaccion de prueba para el simulador (POST /api/antifraude/simular). */
public record TransaccionSimuladaRequest(
        @NotBlank(message = "El email del cliente es obligatorio")
        @Email(message = "El email del cliente no es valido")
        String clienteEmail,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que 0")
        Double monto,

        @NotBlank(message = "El metodo de pago es obligatorio")
        @Pattern(regexp = "DEBITO|CREDITO|EFECTIVO|TRANSFERENCIA",
                message = "El metodo de pago debe ser DEBITO, CREDITO, EFECTIVO o TRANSFERENCIA")
        String metodoPago,

        /** Opcional: false simula un pago rechazado (por defecto aprobado). */
        Boolean aprobado) {
}
