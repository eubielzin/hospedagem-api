package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.DespesaPagamentoRequest;
import com.gestaocasas.application.dto.DespesaRequest;
import com.gestaocasas.application.dto.DespesaResponse;
import com.gestaocasas.application.dto.FluxoCaixaResponse;
import com.gestaocasas.application.dto.PagamentoResponse;
import com.gestaocasas.application.dto.PendenciaResponse;
import com.gestaocasas.application.dto.RecebimentoRepasseRequest;
import com.gestaocasas.application.dto.RepasseResponse;
import com.gestaocasas.application.usecase.financeiro.DespesaUseCase;
import com.gestaocasas.application.usecase.financeiro.FinanceiroUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Financeiro", description = "Recebimentos, despesas, repasses das OTAs, pendências e fluxo de caixa. "
        + "Períodos com inicio e fim inclusive.")
public class FinanceiroController {

    private final FinanceiroUseCase financeiro;
    private final DespesaUseCase despesas;

    public FinanceiroController(FinanceiroUseCase financeiro, DespesaUseCase despesas) {
        this.financeiro = financeiro;
        this.despesas = despesas;
    }

    @GetMapping("/financeiro/recebimentos")
    @Operation(summary = "Recebimentos do período (RF-44)",
            description = "Pagamentos de reservas e de vendas avulsas (reservaId nulo), o mais recente primeiro.")
    public ResponseEntity<ApiResponse<List<PagamentoResponse>>> recebimentos(@RequestParam LocalDate inicio,
                                                                             @RequestParam LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Recebimentos recuperados", financeiro.recebimentos(inicio, fim)));
    }

    @GetMapping("/financeiro/repasses")
    @Operation(summary = "Repasses das OTAs (RF-46, RN-09)", description = "Um por reserva ativa de canal OTA, pela "
            + "previsão (checkout, premissa a validar). comissao usa o percentual do canal na época da reserva.")
    public ResponseEntity<ApiResponse<List<RepasseResponse>>> repasses() {
        return ResponseEntity.ok(ApiResponse.ok("Repasses recuperados", financeiro.repasses()));
    }

    @PatchMapping("/financeiro/repasses/{reservaId}/recebimento")
    @Operation(summary = "Concilia o repasse de uma reserva", description = "422 se já recebido ou se a reserva não "
            + "for de OTA (RN-09).")
    public ResponseEntity<ApiResponse<RepasseResponse>> receberRepasse(@PathVariable UUID reservaId,
                                                                       @Valid @RequestBody RecebimentoRepasseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Repasse recebido", financeiro.receberRepasse(reservaId, request)));
    }

    @GetMapping("/financeiro/pendencias")
    @Operation(summary = "Contas com saldo (RF-47)", description = "Reservas HOSPEDADO ou CHECKOUT_REALIZADO com saldo > 0.")
    public ResponseEntity<ApiResponse<List<PendenciaResponse>>> pendencias() {
        return ResponseEntity.ok(ApiResponse.ok("Pendências recuperadas", financeiro.pendencias()));
    }

    @GetMapping("/financeiro/fluxo")
    @Operation(summary = "Fluxo de caixa (RF-50)", description = "Realizado: pagamentos, repasses recebidos, valores "
            + "retidos e despesas pagas. Previsto: repasses pela previsão e despesas pelo vencimento.")
    public ResponseEntity<ApiResponse<FluxoCaixaResponse>> fluxo(@RequestParam LocalDate inicio,
                                                                 @RequestParam LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Fluxo de caixa calculado", financeiro.fluxo(inicio, fim)));
    }

    @GetMapping("/despesas")
    @Operation(summary = "Lista despesas por vencimento", description = "inicio e fim opcionais filtram o vencimento.")
    public ResponseEntity<ApiResponse<List<DespesaResponse>>> despesas(@RequestParam(required = false) LocalDate inicio,
                                                                       @RequestParam(required = false) LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Despesas recuperadas", despesas.listar(inicio, fim)));
    }

    @PostMapping("/despesas")
    @Operation(summary = "Cadastra uma despesa (RF-45)")
    public ResponseEntity<ApiResponse<DespesaResponse>> criarDespesa(@Valid @RequestBody DespesaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Despesa cadastrada com sucesso", despesas.criar(request)));
    }

    @PatchMapping("/despesas/{id}/pagamento")
    @Operation(summary = "Registra o pagamento da despesa", description = "422 se já paga.")
    public ResponseEntity<ApiResponse<DespesaResponse>> pagarDespesa(@PathVariable UUID id,
                                                                     @Valid @RequestBody DespesaPagamentoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Pagamento da despesa registrado", despesas.pagar(id, request)));
    }
}
