package com.restaurante.model.dto.response;

public record LoginResponse(String token, String tipo, long expirationMs) {
    @Override
    public String toString() {
        return "LoginResponse[tipo=" + tipo + ", expirationMs=" + expirationMs + "]";
    }
}
