# Configuración de Kafka con Docker

## Requisitos
- Docker y Docker Compose instalados
- Java 17 o superior
- Maven

## 1. Iniciar la infraestructura

Ejecuta el siguiente comando en la raíz del proyecto:

```bash
docker-compose up -d
```

Esto iniciará los siguientes servicios:
- Zookeeper en el puerto 2181
- Kafka en el puerto 9092 (y 29092 para conexiones internas)
- Kafka UI en http://localhost:8080
- Kafdrop en http://localhost:9000

## 2. Verificar que todo funcione

1. Abre http://localhost:8080 en tu navegador para ver la interfaz de Kafka UI
2. O usa http://localhost:9000 para Kafdrop (alternativa más ligera)

Deberías ver un clúster de Kafka funcionando con los tópicos que se creen automáticamente.

## 3. Configuración de la aplicación Spring Boot

La aplicación está configurada para conectarse a Kafka en `localhost:9092`.

## Comandos útiles

### Detener los contenedores
```bash
docker-compose down
```

### Ver logs de los contenedores
```bash
docker-compose logs -f
```

### Eliminar volúmenes (reinicia todo desde cero)
```bash
docker-compose down -v
```

## Solución de problemas

Si tienes problemas de conexión:
1. Verifica que los contenedores estén en ejecución con `docker ps`
2. Revisa los logs con `docker-compose logs kafka`
3. Asegúrate de que el puerto 9092 no esté en uso
