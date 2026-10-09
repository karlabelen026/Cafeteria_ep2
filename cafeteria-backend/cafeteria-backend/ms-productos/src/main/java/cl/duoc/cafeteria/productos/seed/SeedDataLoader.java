package cl.duoc.cafeteria.productos.seed;

import cl.duoc.cafeteria.productos.model.Producto;
import cl.duoc.cafeteria.productos.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga la carta inicial de la cafeteria (los productos que ya tienen foto en
 * cafeteria-frontend/public/productos/) la primera vez que el microservicio
 * arranca contra una base vacia, para que la tienda publica tenga menu y
 * fotos desde el primer momento. No hace nada si ya existen productos, asi
 * que nunca pisa lo que el ADMIN haya creado o editado desde el dashboard.
 */
@Component
public class SeedDataLoader implements CommandLineRunner {

    private final ProductoRepository repository;

    public SeedDataLoader(ProductoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        // El orden importa: en una base vacia quedan con ids 1..25, y las recetas
        // iniciales de ms-inventario referencian los primeros por ese id.
        repository.saveAll(List.of(
                producto("Latte Vainilla", "Espresso suave con leche vaporizada y un toque de vainilla.",
                        3200.0, "Bebidas calientes", "/productos/latte-vainilla.jpg"),
                producto("Mocaccino", "Espresso con chocolate, leche vaporizada y un toque de cacao.",
                        3400.0, "Bebidas calientes", "/productos/mocaccino.jpg"),
                producto("Cold Brew", "Café extraído en frío durante horas, suave y refrescante.",
                        3300.0, "Bebidas frías", "/productos/cold-brew.jpg"),
                producto("Frappé de Caramelo", "Café helado batido con hielo y caramelo, cremoso y bien frío.",
                        3600.0, "Bebidas frías", "/productos/frappe-de-caramelo.jpg"),
                producto("Banana Bread", "Queque húmedo de plátano, horneado todos los días.",
                        2800.0, "Pastelería", "/productos/banana-bread.jpg"),
                producto("Brownie", "Brownie de chocolate intenso con el centro bien fudgy.",
                        2500.0, "Pastelería", "/productos/brownie.jpg"),
                producto("Croissant de Almendra", "Croissant relleno de crema de almendra y hojuelas tostadas.",
                        2700.0, "Pastelería", "/productos/croissant-de-almendra.jpg"),
                producto("Red Velvet", "Bizcocho rojo aterciopelado con frosting de queso crema.",
                        3000.0, "Pastelería", "/productos/red-velvet.jpg"),
                producto("Cupcake de Chocolate", "Cupcake de chocolate con frosting cremoso.",
                        2200.0, "Pastelería", "/productos/cupcake-de-chocolate.jpg"),
                producto("Cupcake de Limón", "Cupcake de limón fresco con frosting suave.",
                        2200.0, "Pastelería", "/productos/cupcake-de-limon.jpg"),
                producto("Cupcake de Vainilla y Chispas de Chocolate", "Cupcake de vainilla con chispas de chocolate.",
                        2300.0, "Pastelería", "/productos/cupcake-de-vainilla-y-chispas-de-chocolate.jpg"),
                producto("Caja de Galletas Estilo Crumbl", "Caja surtida de galletas estilo Crumbl, recién horneadas.",
                        4500.0, "Galletas", "/productos/caja-de-galletas-estilo-crumbl.jpg"),
                producto("Café Americano", "Espresso doble alargado con agua caliente.",
                        2200.0, "Bebidas calientes", "/productos/cafe-americano.jpg"),
                producto("Café con Leche", "Café de grano con leche caliente, el clásico de la mañana.",
                        2600.0, "Bebidas calientes", "/productos/cafe-con-leche.jpg"),
                producto("Capuchino", "Espresso, leche vaporizada y abundante espuma.",
                        2900.0, "Bebidas calientes", "/productos/capuchino.jpg"),
                producto("Chocolate Caliente", "Chocolate espeso preparado con leche entera.",
                        3000.0, "Bebidas calientes", "/productos/chocolate-caliente.jpg"),
                producto("Té Verde", "Infusión de té verde en hoja, suave y aromática.",
                        2000.0, "Bebidas calientes", "/productos/te-verde.jpg"),
                producto("Café Helado", "Café frío con hielo y un toque de leche.",
                        3100.0, "Bebidas frías", "/productos/cafe-helado.jpg"),
                producto("Jugo de Naranja", "Jugo de naranja recién exprimido.",
                        2800.0, "Bebidas frías", "/productos/jugo-de-naranja.png"),
                producto("Limonada Natural", "Limonada fresca con menta.",
                        2500.0, "Bebidas frías", "/productos/limonada-natural.jpg"),
                producto("Cheesecake", "Cheesecake cremoso con base de galleta.",
                        3200.0, "Pastelería", "/productos/cheesecake.jpg"),
                producto("Muffin de Arándanos", "Muffin esponjoso con arándanos frescos.",
                        2400.0, "Pastelería", "/productos/muffin-de-arandanos.jpg"),
                producto("Galleta de Avena", "Galleta de avena con pasas, crocante por fuera.",
                        1500.0, "Galletas", "/productos/galleta-de-avena.jpg"),
                producto("Galleta de Chocolate", "Galleta con trozos de chocolate semiamargo.",
                        1500.0, "Galletas", "/productos/galleta-de-chocolate.jpg"),
                producto("Galleta de Mantequilla", "Galleta clásica de mantequilla.",
                        1300.0, "Galletas", "/productos/galleta-de-mantequilla.jpg")
        ));
    }

    private Producto producto(String nombre, String descripcion, Double precio, String categoria, String imagenUrl) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecio(precio);
        producto.setCategoria(categoria);
        producto.setDisponible(true);
        producto.setImagenUrl(imagenUrl);
        return producto;
    }
}
