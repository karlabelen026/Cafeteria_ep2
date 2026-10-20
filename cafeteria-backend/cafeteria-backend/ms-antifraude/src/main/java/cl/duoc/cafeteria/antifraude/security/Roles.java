package cl.duoc.cafeteria.antifraude.security;

/**
 * Nombres de los App Roles definidos en Azure Entra ID, en MAYUSCULAS y sin
 * prefijo (igual que en los demas microservicios). Se usan en los
 * @PreAuthorize de los controllers via T(...).CONSTANTE.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
