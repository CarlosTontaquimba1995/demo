package factura.flow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuración de Redis para el almacenamiento en caché distribuido.
 * 
 * Esta clase configura el cliente de Redis que se utilizará en toda la aplicación
 * para operaciones de caché y almacenamiento temporal de datos.
 * 
 * Características:
 * - Serialización de claves y valores como cadenas de texto
 * - Configuración de la fábrica de conexiones inyectada automáticamente
 * - Plantilla Redis lista para ser inyectada en otros servicios
 * 
 * Uso típico:
 * 1. Inyectar RedisTemplate en los servicios que necesiten acceso a Redis
 * 2. Utilizar los métodos opsFor* para diferentes estructuras de datos
 * 3. Gestionar la expiración de claves según sea necesario
 */
@Configuration
public class RedisConfig {

    /**
     * Configura y devuelve una instancia de RedisTemplate para operaciones con cadenas.
     * 
     * @param connectionFactory Fábrica de conexiones a Redis configurada automáticamente
     * @return Instancia configurada de RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        // Establece la fábrica de conexiones
        template.setConnectionFactory(connectionFactory);
        // Configura el serializador para las claves (siempre String)
        template.setKeySerializer(new StringRedisSerializer());
        // Configura el serializador para los valores (String en este caso)
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
