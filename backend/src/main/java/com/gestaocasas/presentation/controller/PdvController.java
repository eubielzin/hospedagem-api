package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.CancelamentoVendaRequest;
import com.gestaocasas.application.dto.DevolucaoVendaRequest;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.PagamentoVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaRequest;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.dto.VendaResumoResponse;
import com.gestaocasas.application.usecase.pdv.CancelarVendaUseCase;
import com.gestaocasas.application.usecase.pdv.DevolverItensVendaUseCase;
import com.gestaocasas.application.usecase.pdv.GetVendaByIdUseCase;
import com.gestaocasas.application.usecase.pdv.ListarVendasUseCase;
import com.gestaocasas.application.usecase.pdv.RegistrarPagamentoVendaUseCase;
import com.gestaocasas.application.usecase.pdv.RegistrarVendaUseCase;
import com.gestaocasas.domain.enums.StatusVenda;
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
@RequestMapping("/pdv/vendas")
@Tag(name = "PDV", description = "Vendas avulsas por imóvel. O consumo de hóspede vai para a conta da reserva "
        + "(POST /reservas/{id}/lancamentos).")
public class PdvController {

    public static final String CABECALHO_CHAVE = "Idempotency-Key";
    public static final String CABECALHO_REPETIDA = "Idempotent-Replay";
    public static final String FORMATO_CHAVE = "[A-Za-z0-9_.:-]{8,100}";
    public static final String CHAVE_INVALIDA =
            "Idempotency-Key deve ter de 8 a 100 caracteres: letras, números, '-', '_', '.' ou ':'";

    private final RegistrarVendaUseCase registrarVenda;
    private final ListarVendasUseCase listarVendas;
    private final GetVendaByIdUseCase getVendaById;
    private final CancelarVendaUseCase cancelarVenda;
    private final DevolverItensVendaUseCase devolverItens;
    private final RegistrarPagamentoVendaUseCase registrarPagamento;

    public PdvController(RegistrarVendaUseCase registrarVenda, ListarVendasUseCase listarVendas,
                         GetVendaByIdUseCase getVendaById, CancelarVendaUseCase cancelarVenda,
                         DevolverItensVendaUseCase devolverItens, RegistrarPagamentoVendaUseCase registrarPagamento) {
        this.registrarVenda = registrarVenda;
        this.listarVendas = listarVendas;
        this.getVendaById = getVendaById;
        this.cancelarVenda = cancelarVenda;
        this.devolverItens = devolverItens;
        this.registrarPagamento = registrarPagamento;
    }

    @PostMapping
    @Operation(summary = "Registra uma venda avulsa e baixa o estoque do imóvel",
            description = "Os preços vêm do cadastro; não há campo de preço na requisição. Responde 422 se "
                    + "faltar saldo no imóvel e 409 se totalEsperado divergir do total calculado. Exige o "
                    + "cabeçalho Idempotency-Key, único por venda: repetir a requisição com a mesma chave "
                    + "devolve a venda já registrada, com Idempotent-Replay: true. A venda nasce com "
                    + "pagamentoStatus PENDENTE.")
    public ResponseEntity<ApiResponse<VendaResponse>> registrar(
            @RequestHeader(CABECALHO_CHAVE) @Pattern(regexp = FORMATO_CHAVE, message = CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody VendaRequest request) {
        ResultadoIdempotente<VendaResponse> resultado = registrarVenda.execute(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(ApiResponse.ok(resultado.repetida() ? "Venda já registrada com esta chave"
                        : "Venda registrada com sucesso", resultado.valor()));
    }

    @GetMapping
    @Operation(summary = "Lista vendas, da mais recente para a mais antiga",
            description = "O parâmetro sort é ignorado.")
    public ResponseEntity<ApiResponse<PageResponse<VendaResumoResponse>>> list(
            @RequestParam(required = false) UUID imovelId,
            @RequestParam(required = false) StatusVenda status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Vendas recuperadas com sucesso",
                listarVendas.execute(imovelId, status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendaResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Venda encontrada", getVendaById.execute(id)));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancela a venda e devolve ao estoque o que ainda não tinha sido devolvido",
            description = "Cancelar uma venda já cancelada não altera nada e devolve o estado atual.")
    public ResponseEntity<ApiResponse<VendaResponse>> cancelar(
            @PathVariable UUID id, @Valid @RequestBody CancelamentoVendaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Venda cancelada com sucesso", cancelarVenda.execute(id, request)));
    }

    @PostMapping("/{id}/devolucoes")
    @Operation(summary = "Registra a devolução física de itens da venda ao estoque do imóvel",
            description = "Aceita devolução parcial. Não registra reembolso. Exige o cabeçalho Idempotency-Key.")
    public ResponseEntity<ApiResponse<VendaResponse>> devolver(
            @PathVariable UUID id,
            @RequestHeader(CABECALHO_CHAVE) @Pattern(regexp = FORMATO_CHAVE, message = CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody DevolucaoVendaRequest request) {
        ResultadoIdempotente<VendaResponse> resultado = devolverItens.execute(id, request, idempotencyKey);
        return ResponseEntity.ok()
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(ApiResponse.ok(resultado.repetida() ? "Devolução já registrada com esta chave"
                        : "Devolução registrada com sucesso", resultado.valor()));
    }

    @PostMapping("/{id}/pagamento")
    @Operation(summary = "Registra o pagamento da venda (RF-39)",
            description = "O valor é o total menos o devolvido, calculado aqui. Em DINHEIRO, valorRecebido é "
                    + "obrigatório e maior ou igual ao valor (400), e a resposta traz o troco. 422 se a venda estiver "
                    + "cancelada ou já paga. Exige Idempotency-Key: repetir com a mesma chave devolve 200 com "
                    + "Idempotent-Replay: true.")
    public ResponseEntity<ApiResponse<VendaResponse>> pagar(
            @PathVariable UUID id,
            @RequestHeader(CABECALHO_CHAVE) @Pattern(regexp = FORMATO_CHAVE, message = CHAVE_INVALIDA)
            String idempotencyKey,
            @Valid @RequestBody PagamentoVendaRequest request) {
        ResultadoIdempotente<VendaResponse> resultado = registrarPagamento.execute(id, request, idempotencyKey);
        return ResponseEntity.ok()
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(ApiResponse.ok(resultado.repetida() ? "Pagamento já registrado com esta chave"
                        : "Pagamento registrado com sucesso", resultado.valor()));
    }
}
