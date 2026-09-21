package com.restaurante.exception;

public class InvalidOrderStateException extends BusinessRuleException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
