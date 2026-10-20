package cl.duoc.cafeteria.antifraude.streams;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversion y redondeo de montos usados por la topologia. */
final class Montos {

    private Montos() {
    }

    static double aUsd(double montoClp, double tasaClpUsd) {
        return redondear(montoClp / tasaClpUsd);
    }

    static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
