package factura.flow.exception;

/**
 * Excepción que indica que una operación falló pero puede ser reintentada.
 * 
 * <p>Esta excepción es utilizada para marcar errores transitorios o temporales
 * que podrían resolverse con reintentos posteriores. Los componentes que manejan
 * esta excepción pueden implementar lógica de reintentos automáticos.</p>
 * 
 * <p>Ejemplos de uso típicos incluyen:
 * <ul>
 *   <li>Errores de red temporales</li>
 *   <li>Servicios externos no disponibles temporalmente</li>
 *   <li>Bloqueos temporales de recursos</li>
 * </ul>
 * </p>
 * 
 * @see java.lang.RuntimeException
 */
public class RetryableException extends RuntimeException {
    /**
     * Construye una nueva excepción con el mensaje de error especificado.
     *
     * @param message el mensaje de error detallado
     */
    public RetryableException(String message) {
        super(message);
    }

    /**
     * Construye una nueva excepción con el mensaje de error y la causa especificados.
     *
     * @param message el mensaje de error detallado
     * @param cause la causa de la excepción
     */
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
