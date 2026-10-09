-- Se ejecuta automaticamente al levantar el contenedor de MySQL (docker-compose).
-- En MySQL "CREATE SCHEMA" es sinonimo de "CREATE DATABASE": cada sentencia crea
-- una base propia por microservicio (ademas de "cafeteria", la que crea
-- MYSQL_DATABASE), que es lo que selecciona cada uno via
-- spring.jpa.properties.hibernate.default_schema en su application.yml.
CREATE SCHEMA IF NOT EXISTS productos;
CREATE SCHEMA IF NOT EXISTS inventario;
CREATE SCHEMA IF NOT EXISTS pedidos;
CREATE SCHEMA IF NOT EXISTS clientes;
CREATE SCHEMA IF NOT EXISTS pagos;
CREATE SCHEMA IF NOT EXISTS empleados;
CREATE SCHEMA IF NOT EXISTS proveedores;
CREATE SCHEMA IF NOT EXISTS reportes;
CREATE SCHEMA IF NOT EXISTS notificaciones;

-- El usuario de la aplicacion (MYSQL_USER) solo recibe permisos sobre MYSQL_DATABASE:
-- hay que darselos explicitamente sobre cada base. docker-compose.yml repite esto
-- mismo en el servicio mysql-init para instalaciones que ya tenian datos.
GRANT ALL PRIVILEGES ON productos.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON inventario.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON pedidos.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON clientes.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON pagos.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON empleados.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON proveedores.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON reportes.* TO 'cafeteria'@'%';
GRANT ALL PRIVILEGES ON notificaciones.* TO 'cafeteria'@'%';
