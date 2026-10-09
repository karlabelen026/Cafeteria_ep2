package cl.duoc.cafeteria.inventario.seed;

import cl.duoc.cafeteria.inventario.model.Insumo;
import cl.duoc.cafeteria.inventario.model.RecetaItem;
import cl.duoc.cafeteria.inventario.repository.InsumoRepository;
import cl.duoc.cafeteria.inventario.repository.RecetaItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga los insumos y recetas iniciales la primera vez que el microservicio
 * arranca contra una base vacia. Sin recetas, un pago aprobado no descuenta
 * stock y nunca se genera la alerta de stock bajo. No hace nada si ya
 * existen insumos (nunca pisa lo que se haya editado desde el dashboard).
 */
@Component
public class SeedDataLoader implements CommandLineRunner {

    private final InsumoRepository insumoRepository;
    private final RecetaItemRepository recetaItemRepository;

    public SeedDataLoader(InsumoRepository insumoRepository, RecetaItemRepository recetaItemRepository) {
        this.insumoRepository = insumoRepository;
        this.recetaItemRepository = recetaItemRepository;
    }

    @Override
    public void run(String... args) {
        if (insumoRepository.count() > 0) {
            return;
        }

        Insumo cafeEnGrano = insumoRepository.save(insumo("Café en grano", "g", 4000.0, 1500.0));
        Insumo leche = insumoRepository.save(insumo("Leche", "ml", 8000.0, 3000.0));
        // stockActual <= stockMinimo a proposito, para que /api/inventario/alertas tenga datos.
        Insumo azucar = insumoRepository.save(insumo("Azúcar", "g", 500.0, 1000.0));
        Insumo vasos = insumoRepository.save(insumo("Vasos para bebidas calientes", "unidad", 300.0, 100.0));
        Insumo servilletas = insumoRepository.save(insumo("Servilletas", "unidad", 600.0, 200.0));
        insumoRepository.save(insumo("Harina", "g", 5000.0, 2000.0));

        // productoId = ids 1..5 de la carta inicial de ms-productos (Latte
        // Vainilla, Mocaccino, Cold Brew, Frappé de Caramelo, Banana Bread).
        recetaItemRepository.saveAll(List.of(
                receta(1L, cafeEnGrano.getId(), 18.0),
                receta(1L, leche.getId(), 150.0),
                receta(2L, cafeEnGrano.getId(), 18.0),
                receta(2L, leche.getId(), 100.0),
                receta(3L, cafeEnGrano.getId(), 20.0),
                receta(4L, vasos.getId(), 1.0),
                receta(4L, servilletas.getId(), 1.0),
                receta(5L, azucar.getId(), 10.0)
        ));
    }

    private Insumo insumo(String nombre, String unidadMedida, Double stockActual, Double stockMinimo) {
        Insumo insumo = new Insumo();
        insumo.setNombre(nombre);
        insumo.setUnidadMedida(unidadMedida);
        insumo.setStockActual(stockActual);
        insumo.setStockMinimo(stockMinimo);
        return insumo;
    }

    private RecetaItem receta(Long productoId, Long insumoId, Double cantidad) {
        RecetaItem item = new RecetaItem();
        item.setProductoId(productoId);
        item.setInsumoId(insumoId);
        item.setCantidad(cantidad);
        return item;
    }
}
