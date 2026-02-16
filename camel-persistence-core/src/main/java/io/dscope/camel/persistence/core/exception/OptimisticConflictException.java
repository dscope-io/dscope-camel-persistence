package io.dscope.camel.persistence.core.exception;

public class OptimisticConflictException extends PersistenceException {

    public OptimisticConflictException(String message) {
        super(message);
    }
}
