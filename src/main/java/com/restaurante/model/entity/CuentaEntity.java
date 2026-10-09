package com.restaurante.model.entity;

import com.restaurante.model.domain.enums.EstadoCuenta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cuentas", uniqueConstraints = @UniqueConstraint(
        name = "uk_cuentas_mesa_abierta", columnNames = {"mesa_id", "cuenta_abierta"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mesa_id", nullable = false)
    private MesaEntity mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /**
     * Columna técnica generada por PostgreSQL. Vale TRUE únicamente para una
     * cuenta ABIERTA y NULL para las cerradas, de modo que la restricción única
     * permita el historial de cuentas cerradas y rechace dos abiertas por mesa.
     */
    @Column(name = "cuenta_abierta", insertable = false, updatable = false,
            columnDefinition = "boolean generated always as "
                    + "(case when estado = 'ABIERTA' then true else null end) stored")
    private Boolean cuentaAbierta;

    @OneToMany(mappedBy = "cuenta", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PedidoEntity> pedidos = new ArrayList<>();
}
