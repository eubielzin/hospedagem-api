package com.gestaocasas.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** RF-51 a RF-53 e RN-12. regimePensao vem como texto legível ("Café da manhã"). */
public record PessoasDoDiaResponse(LocalDate data, List<Linha> linhas, int totalAdultos, int totalCriancas) {

    public record Linha(UUID reservaId, String codigo, String uhNome, String titularNome, int adultos, int criancas,
                        String regimePensao, String observacoesAlimentares) {
    }
}
