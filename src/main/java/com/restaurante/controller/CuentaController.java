package com.restaurante.controller;

import com.restaurante.mapper.CuentaMapper;
import com.restaurante.model.dto.response.CuentaResponse;
import com.restaurante.service.CuentaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
@Tag(name = "Cuentas")
public class CuentaController {
    private final CuentaService service;
    private final CuentaMapper mapper;

    @GetMapping
    public List<CuentaResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CuentaResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }
}
