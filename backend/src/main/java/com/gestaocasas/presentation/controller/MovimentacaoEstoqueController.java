package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.AjusteEstoqueRequest;
import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.EntradaEstoqueRequest;
import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.TransferenciaEstoqueRequest;
import com.gestaocasas.application.dto.TransferenciaEstoqueResponse;
import com.gestaocasas.application.usecase.estoque.ListarMovimentacoesEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.RegistrarAjusteEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.RegistrarEntradaEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.TransferirEstoqueUseCase;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Movimentações de estoque", description = "Entradas, ajustes, perdas e transferências. Cada "
        + "operação gera um registro imutável com o saldo anterior e o posterior. Bloqueado (403) fora do "
        + "ambiente local enquanto a API não tiver autenticação.")
public class MovimentacaoEstoqueController {

    private static final String ESTOQUE_INSUFICIENTE = " Responde 422 se o saldo do imóvel não for suficiente.";
    private static final String CHAVE_OPCIONAL = " Com o cabeçalho opcional Idempotency-Key, repetir a requisição "
            + "devolve a operação já registrada (Idempotent-Replay: true) em vez de lançá-la de novo.";

    private final ListarMovimentacoesEstoqueUseCase listarMovimentacoes;
    private final RegistrarEntradaEstoqueUseCase registrarEntrada;
    private final RegistrarAjusteEstoqueUseCase registrarAjuste;
    private final TransferirEstoqueUseCase transferirEstoque;

    public MovimentacaoEstoqueController(ListarMovimentacoesEstoqueUseCase listarMovimentacoes,
                                         RegistrarEntradaEstoqueUseCase registrarEntrada,
                                         RegistrarAjusteEstoqueUseCase registrarAjuste,
                                         TransferirEstoqueUseCase transferirEstoque) {
        this.listarMovimentacoes = listarMovimentacoes;
        this.registrarEntrada = registrarEntrada;
        this.registrarAjuste = registrarAjuste;
        this.transferirEstoque = transferirEstoque;
    }

    @GetMapping("/imoveis/{imovelId}/estoque/movimentacoes")
    @Operation(summary = "Lista o histórico de movimentações do imóvel, da mais recente para a mais antiga",
            description = "O parâmetro sort é ignorado.")
    public ResponseEntity<ApiResponse<PageResponse<MovimentacaoEstoqueResponse>>> list(
            @PathVariable UUID imovelId,
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) TipoMovimentacao tipo,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Movimentações recuperadas com sucesso",
                listarMovimentacoes.execute(imovelId, produtoId, tipo, pageable)));
    }

    @PostMapping("/imoveis/{imovelId}/estoque/entradas")
    @Operation(summary = "Registra a entrada de mercadoria no estoque do imóvel", description = CHAVE_OPCIONAL)
    public ResponseEntity<ApiResponse<MovimentacaoEstoqueResponse>> entrada(
            @PathVariable UUID imovelId,
            @RequestHeader(value = PdvController.CABECALHO_CHAVE, required = false)
            @Pattern(regexp = PdvController.FORMATO_CHAVE, message = PdvController.CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody EntradaEstoqueRequest request) {
        return criado("Entrada registrada com sucesso", registrarEntrada.execute(imovelId, request, idempotencyKey));
    }

    @PostMapping("/imoveis/{imovelId}/estoque/ajustes")
    @Operation(summary = "Registra um ajuste de inventário ou uma perda",
            description = "Tipos: AJUSTE_POSITIVO, AJUSTE_NEGATIVO e PERDA. O motivo é obrigatório."
                    + ESTOQUE_INSUFICIENTE + CHAVE_OPCIONAL)
    public ResponseEntity<ApiResponse<MovimentacaoEstoqueResponse>> ajuste(
            @PathVariable UUID imovelId,
            @RequestHeader(value = PdvController.CABECALHO_CHAVE, required = false)
            @Pattern(regexp = PdvController.FORMATO_CHAVE, message = PdvController.CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody AjusteEstoqueRequest request) {
        return criado("Ajuste registrado com sucesso", registrarAjuste.execute(imovelId, request, idempotencyKey));
    }

    @PostMapping("/estoque/transferencias")
    @Operation(summary = "Transfere um produto do estoque de um imóvel para o de outro",
            description = "Gera uma saída na origem e uma entrada no destino, com a mesma referência."
                    + ESTOQUE_INSUFICIENTE + CHAVE_OPCIONAL)
    public ResponseEntity<ApiResponse<TransferenciaEstoqueResponse>> transferir(
            @RequestHeader(value = PdvController.CABECALHO_CHAVE, required = false)
            @Pattern(regexp = PdvController.FORMATO_CHAVE, message = PdvController.CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody TransferenciaEstoqueRequest request) {
        return criado("Transferência registrada com sucesso", transferirEstoque.execute(request, idempotencyKey));
    }

    private static <T> ResponseEntity<ApiResponse<T>> criado(String mensagem, ResultadoIdempotente<T> resultado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(PdvController.CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(ApiResponse.ok(resultado.repetida() ? "Operação já registrada com esta chave" : mensagem,
                        resultado.valor()));
    }
}
