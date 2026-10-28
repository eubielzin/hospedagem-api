package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.MeioTransporte;
import com.gestaocasas.domain.enums.MotivoViagem;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** RF-23: dados de viagem da Ficha Nacional de Registro de Hóspedes. Exigida no check-in (RN-04). */
@Embeddable
@Getter
@Setter
public class Fnrh {

    @Column(name = "fnrh_cidade_procedencia", length = 120)
    private String cidadeProcedencia;

    @Column(name = "fnrh_cidade_destino", length = 120)
    private String cidadeDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "fnrh_motivo_viagem", length = 20)
    private MotivoViagem motivoViagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "fnrh_meio_transporte", length = 20)
    private MeioTransporte meioTransporte;

    @Column(name = "fnrh_emitida_em")
    private LocalDate emitidaEm;
}
