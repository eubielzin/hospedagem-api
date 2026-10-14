package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.EstoqueMinimoRequest;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.usecase.estoque.DefinirEstoqueMinimoUseCase;
import com.gestaocasas.application.usecase.estoque.ListarAlertasEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.ListarEstoqueImovelUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/imoveis/{imovelId}/estoque")
@Tag(name = "Estoque por imóvel", description = "Cada imóvel tem o seu próprio saldo de cada produto.")
public class EstoqueImovelController {

    private final ListarEstoqueImovelUseCase listarEstoque;
    private final ListarAlertasEstoqueUseCase listarAlertas;
    private final DefinirEstoqueMinimoUseCase definirEstoqueMinimo;

    public EstoqueImovelController(ListarEstoqueImovelUseCase listarEstoque, ListarAlertasEstoqueUseCase listarAlertas,
                                   DefinirEstoqueMinimoUseCase definirEstoqueMinimo) {
        this.listarEstoque = listarEstoque;
        this.listarAlertas = listarAlertas;
        this.definirEstoqueMinimo = definirEstoqueMinimo;
    }

    @GetMapping
    @Operation(summary = "Lista o saldo de cada produto neste imóvel, em ordem de nome",
            description = "Traz todos os produtos que controlam estoque; os que o imóvel nunca teve aparecem "
                    + "com quantidade zero. O parâmetro sort é ignorado.")
    public ResponseEntity<ApiResponse<PageResponse<EstoqueItemResponse>>> list(
            @PathVariable UUID imovelId,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Estoque recuperado com sucesso",
                listarEstoque.execute(imovelId, nome, ativo, pageable)));
    }

    @GetMapping("/alertas")
    @Operation(summary = "Lista os produtos ativos deste imóvel com saldo igual ou abaixo do mínimo",
            description = "Produtos sem mínimo definido (zero) não geram alerta. O parâmetro sort é ignorado.")
    public ResponseEntity<ApiResponse<PageResponse<EstoqueItemResponse>>> alertas(
            @PathVariable UUID imovelId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Alertas de estoque recuperados com sucesso",
                listarAlertas.execute(imovelId, pageable)));
    }

    @PutMapping("/{produtoId}")
    @Operation(summary = "Define o estoque mínimo do produto neste imóvel",
            description = "Não altera a quantidade: ela só muda por movimentações de estoque.")
    public ResponseEntity<ApiResponse<EstoqueItemResponse>> definirMinimo(
            @PathVariable UUID imovelId, @PathVariable UUID produtoId,
            @Valid @RequestBody EstoqueMinimoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Estoque mínimo definido com sucesso",
                definirEstoqueMinimo.execute(imovelId, produtoId, request)));
    }
}
