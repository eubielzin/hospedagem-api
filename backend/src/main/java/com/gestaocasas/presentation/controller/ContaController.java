package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.ContaResponse;
import com.gestaocasas.application.dto.EstornoRequest;
import com.gestaocasas.application.dto.LancamentoRequest;
import com.gestaocasas.application.dto.PagamentoRequest;
import com.gestaocasas.application.usecase.conta.GetContaUseCase;
import com.gestaocasas.application.usecase.conta.LancamentoContaUseCase;
import com.gestaocasas.application.usecase.conta.RegistrarPagamentoReservaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reservas/{reservaId}")
@Tag(name = "Conta do hóspede", description = "saldo = diarias − repasseCanal + consumos − pagamentos. Em reservas de "
        + "OTA as diárias são recebidas do canal (RN-09). Toda ação devolve a conta atualizada.")
public class ContaController {

    private final GetContaUseCase getConta;
    private final LancamentoContaUseCase lancamentos;
    private final RegistrarPagamentoReservaUseCase registrarPagamento;

    public ContaController(GetContaUseCase getConta, LancamentoContaUseCase lancamentos,
                          RegistrarPagamentoReservaUseCase registrarPagamento) {
        this.getConta = getConta;
        this.lancamentos = lancamentos;
        this.registrarPagamento = registrarPagamento;
    }

    @GetMapping("/conta")
    public ResponseEntity<ApiResponse<ContaResponse>> conta(@PathVariable UUID reservaId) {
        return ResponseEntity.ok(ApiResponse.ok("Conta recuperada com sucesso", getConta.execute(reservaId)));
    }

    @PostMapping("/lancamentos")
    @Operation(summary = "Lança um consumo na conta (RF-38)",
            description = "Só com a reserva HOSPEDADO (422 RN-06). Com produtoId o preço é o do cadastro e o estoque do "
                    + "imóvel é baixado (422 se faltar saldo); sem produto, descricao e valorUnitario são obrigatórios.")
    public ResponseEntity<ApiResponse<ContaResponse>> lancar(@PathVariable UUID reservaId,
                                                             @Valid @RequestBody LancamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Lançamento registrado com sucesso", lancamentos.lancar(reservaId, request)));
    }

    @PostMapping("/lancamentos/{lancamentoId}/estorno")
    @Operation(summary = "Estorna um lançamento (RF-40)",
            description = "Motivo obrigatório; não estorna duas vezes (422). Produto com estoque volta ao estoque.")
    public ResponseEntity<ApiResponse<ContaResponse>> estornar(@PathVariable UUID reservaId,
                                                               @PathVariable UUID lancamentoId,
                                                               @Valid @RequestBody EstornoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Lançamento estornado com sucesso",
                lancamentos.estornar(reservaId, lancamentoId, request)));
    }

    @PostMapping("/pagamentos")
    @Operation(summary = "Registra um recebimento na conta (RF-44)",
            description = "Reserva cancelada ou no-show: 422 RN-03. valorBruto > 0 e 0 <= taxa <= valorBruto (400).")
    public ResponseEntity<ApiResponse<ContaResponse>> pagar(@PathVariable UUID reservaId,
                                                            @Valid @RequestBody PagamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pagamento registrado com sucesso", registrarPagamento.execute(reservaId, request)));
    }
}
