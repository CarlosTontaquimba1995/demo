package factura.flow.model;

import lombok.Getter;

@Getter
public enum InvoiceStatus {
    NO_FIRMADO("NO_FIRMADO"),
    NO_WS1("NO_WS1"),
    NO_WS2("NO_WS2"),
    NO_ZIP("NO_ZIP"),
    COMPLETED("COMPLETED"),
    ERROR("ERROR");

    private final String value;

    InvoiceStatus(String value) {
        this.value = value;
    }

    public static InvoiceStatus fromValue(String value) {
        for (InvoiceStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return ERROR;
    }
}
