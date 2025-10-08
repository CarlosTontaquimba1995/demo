package factura.flow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Clase que representa una respuesta genérica de la API.
 * 
 * Esta clase se utiliza para estandarizar las respuestas de la API,
 * proporcionando un estado y un mensaje descriptivo.
 * 
 * @param <T> Tipo de datos contenido en la respuesta
 */
public class ApiResponse<T> {
    private final boolean success;
    private final String status;
    private final T data;

    @JsonCreator
    public ApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("status") String status,
            @JsonProperty("data") T data) {
        this.success = success;
        this.status = status;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "COMPLETADO", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, "ERROR", null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }
}
