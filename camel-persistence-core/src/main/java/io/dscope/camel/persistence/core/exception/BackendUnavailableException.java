package io.dscope.camel.persistence.core.exception;

public class BackendUnavailableException extends PersistenceException {

    public BackendUnavailableException(String message) {
        super(message);
    }

    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
