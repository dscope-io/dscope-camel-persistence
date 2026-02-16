package io.dscope.camel.persistence.core.exception;

public class DuplicateEventException extends PersistenceException {

    public DuplicateEventException(String message) {
        super(message);
    }
}
