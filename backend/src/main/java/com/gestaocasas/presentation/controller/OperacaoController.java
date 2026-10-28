package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.ConfiguracaoRequest;
import com.gestaocasas.application.dto.ConfiguracaoResponse;
import com.gestaocasas.application.dto.PessoasDoDiaResponse;
import com.gestaocasas.application.dto.ResumoDiaResponse;
import com.gestaocasas.application.usecase.operacao.ConfiguracaoUseCase;
import com.gestaocasas.application.usecase.operacao.PainelDoDiaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@Tag(name = "Operação", description = "Painel do dia, pessoas do dia e configuração da pousada.")
public class OperacaoController {

    private final PainelDoDiaUseCase painel;
    private final ConfiguracaoUseCase configuracao;
    private final Clock clock;

    public OperacaoController(PainelDoDiaUseCase painel, ConfiguracaoUseCase configuracao, Clock clock) {
        this.painel = painel;
        this.configuracao = configuracao;
        this.clock = clock;
    }

    @GetMapping("/painel/resumo")
    @Operation(summary = "Resumo do dia (RF-55)", description = "Sem data, hoje. Ocupação só das casas ativas; "
            + "diariasPrevistas = Σ valorTotal / noites das reservas na data; adr e revpar a partir dela.")
    public ResponseEntity<ApiResponse<ResumoDiaResponse>> resumo(@RequestParam(required = false) LocalDate data) {
        return ResponseEntity.ok(ApiResponse.ok("Resumo do dia calculado", painel.resumo(dia(data))));
    }

    @GetMapping("/pessoas-do-dia")
    @Operation(summary = "Hóspedes na casa na noite da data (RF-51 a RF-53)",
            description = "Sem data, hoje. Só reservas HOSPEDADO com checkin <= data < checkout, por UH.")
    public ResponseEntity<ApiResponse<PessoasDoDiaResponse>> pessoas(@RequestParam(required = false) LocalDate data) {
        return ResponseEntity.ok(ApiResponse.ok("Pessoas do dia recuperadas", painel.pessoas(dia(data))));
    }

    @GetMapping("/configuracao")
    public ResponseEntity<ApiResponse<ConfiguracaoResponse>> configuracao() {
        return ResponseEntity.ok(ApiResponse.ok("Configuração recuperada", configuracao.buscar()));
    }

    @PutMapping("/configuracao")
    @Operation(summary = "Atualiza a configuração (RF-67)", description = "Horários em HH:mm.")
    public ResponseEntity<ApiResponse<ConfiguracaoResponse>> atualizar(@Valid @RequestBody ConfiguracaoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Configuração atualizada com sucesso", configuracao.atualizar(request)));
    }

    private LocalDate dia(LocalDate data) {
        return data == null ? LocalDate.now(clock) : data;
    }
}
