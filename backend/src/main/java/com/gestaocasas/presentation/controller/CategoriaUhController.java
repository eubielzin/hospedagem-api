package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.CategoriaUhRequest;
import com.gestaocasas.application.dto.CategoriaUhResponse;
import com.gestaocasas.application.usecase.categoria.ListCategoriasUseCase;
import com.gestaocasas.application.usecase.categoria.SalvarCategoriaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categorias")
@Tag(name = "Categorias de UH")
public class CategoriaUhController {

    private final ListCategoriasUseCase listCategorias;
    private final SalvarCategoriaUseCase salvarCategoria;

    public CategoriaUhController(ListCategoriasUseCase listCategorias, SalvarCategoriaUseCase salvarCategoria) {
        this.listCategorias = listCategorias;
        this.salvarCategoria = salvarCategoria;
    }

    @GetMapping
    @Operation(summary = "Lista categorias por nível e nome")
    public ResponseEntity<ApiResponse<List<CategoriaUhResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Categorias recuperadas com sucesso", listCategorias.execute()));
    }

    @PostMapping
    @Operation(summary = "Cadastra uma categoria", description = "Responde 409 se o nome já existir.")
    public ResponseEntity<ApiResponse<CategoriaUhResponse>> create(@Valid @RequestBody CategoriaUhRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Categoria criada com sucesso", salvarCategoria.criar(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoriaUhResponse>> update(@PathVariable UUID id,
                                                                   @Valid @RequestBody CategoriaUhRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Categoria atualizada com sucesso",
                salvarCategoria.atualizar(id, request)));
    }
}
