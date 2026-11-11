package cl.duoc.cafeteria.reportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Reloj inyectable: en las pruebas se reemplaza por uno fijo (ver MetricasTiempoRealServiceImplTest). */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
