package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.ProdutoBuscaResponse;
import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.usecase.produto.*;
import com.gestaocasas.domain.enums.CategoriaProduto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Catálogo único da plataforma. Bloqueado (403) fora do ambiente local "
        + "enquanto a API não tiver autenticação.")
public class ProdutoController {

    private final CreateProdutoUseCase createProduto;
    private final ListProdutosUseCase listProdutos;
    private final GetProdutoByIdUseCase getProdutoById;
    private final UpdateProdutoUseCase updateProduto;
    private final InativarProdutoUseCase inativarProduto;
    private final BuscarProdutosUseCase buscarProdutos;

    public ProdutoController(CreateProdutoUseCase createProduto, ListProdutosUseCase listProdutos,
                             GetProdutoByIdUseCase getProdutoById, UpdateProdutoUseCase updateProduto,
                             InativarProdutoUseCase inativarProduto, BuscarProdutosUseCase buscarProdutos) {
        this.createProduto = createProduto;
        this.listProdutos = listProdutos;
        this.getProdutoById = getProdutoById;
        this.updateProduto = updateProduto;
        this.inativarProduto = inativarProduto;
        this.buscarProdutos = buscarProdutos;
    }

    @PostMapping
    @Operation(summary = "Cadastra um produto", description = "Responde 409 se o código ou o código de barras "
            + "já pertencer a outro produto.")
    public ResponseEntity<ApiResponse<ProdutoResponse>> create(@Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Produto criado com sucesso", createProduto.execute(request)));
    }

    @GetMapping
    @Operation(summary = "Lista produtos com filtros e paginação")
    public ResponseEntity<ApiResponse<PageResponse<ProdutoResponse>>> list(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) CategoriaProduto categoria,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Produtos recuperados com sucesso",
                listProdutos.execute(nome, categoria, ativo, pageable)));
    }

    @GetMapping("/buscar")
    @Operation(summary = "Busca dinâmica para autocompletar, a partir de um caractere",
            description = "Procura o termo em qualquer parte do nome ou do código e no início do código de "
                    + "barras, sem diferenciar maiúsculas nem acentos. Com imovelId, devolve o saldo somente "
                    + "daquele imóvel e lista primeiro os produtos disponíveis nele. O limit é reduzido ao "
                    + "máximo configurado.")
    public ResponseEntity<ApiResponse<List<ProdutoBuscaResponse>>> buscar(
            @RequestParam @NotBlank(message = "Termo é obrigatório")
            @Size(max = 100, message = "Termo deve ter no máximo 100 caracteres") String termo,
            @RequestParam(required = false) UUID imovelId,
            @RequestParam(defaultValue = "true") boolean somenteAtivos,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Limit deve ser no mínimo 1") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Produtos encontrados",
                buscarProdutos.execute(termo, imovelId, somenteAtivos, limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProdutoResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Produto encontrado", getProdutoById.execute(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um produto", description = "Sem o campo ativo, a situação atual é mantida; "
            + "com ativo=true, o produto é reativado.")
    public ResponseEntity<ApiResponse<ProdutoResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Produto atualizado com sucesso", updateProduto.execute(id, request)));
    }

    @PatchMapping("/{id}/inativar")
    public ResponseEntity<ApiResponse<ProdutoResponse>> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Produto inativado com sucesso", inativarProduto.execute(id)));
    }
}
