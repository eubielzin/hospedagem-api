package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.dto.ImovelResumoResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.usecase.imovel.*;
import com.gestaocasas.domain.enums.StatusImovel;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/imoveis")
@Tag(name = "Imóveis")
public class ImovelController {

    private final CreateImovelUseCase createImovel;
    private final ListImoveisUseCase listImoveis;
    private final GetImovelByIdUseCase getImovelById;
    private final UpdateImovelUseCase updateImovel;
    private final InativarImovelUseCase inativarImovel;
    private final AddImovelFotoUseCase addImovelFoto;
    private final RemoveImovelFotoUseCase removeImovelFoto;

    public ImovelController(CreateImovelUseCase createImovel, ListImoveisUseCase listImoveis,
                            GetImovelByIdUseCase getImovelById, UpdateImovelUseCase updateImovel,
                            InativarImovelUseCase inativarImovel, AddImovelFotoUseCase addImovelFoto,
                            RemoveImovelFotoUseCase removeImovelFoto) {
        this.createImovel = createImovel;
        this.listImoveis = listImoveis;
        this.getImovelById = getImovelById;
        this.updateImovel = updateImovel;
        this.inativarImovel = inativarImovel;
        this.addImovelFoto = addImovelFoto;
        this.removeImovelFoto = removeImovelFoto;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImovelResponse>> create(@Valid @RequestBody ImovelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Imóvel criado com sucesso", createImovel.execute(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ImovelResumoResponse>>> list(
            @RequestParam(required = false) StatusImovel status,
            @RequestParam(required = false) String nome,
            @ParameterObject @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Imóveis recuperados com sucesso",
                listImoveis.execute(status, nome, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ImovelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Imóvel encontrado", getImovelById.execute(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImovelResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody ImovelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Imóvel atualizado com sucesso", updateImovel.execute(id, request)));
    }

    @PatchMapping("/{id}/inativar")
    public ResponseEntity<ApiResponse<ImovelResponse>> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Imóvel inativado com sucesso", inativarImovel.execute(id)));
    }

    @PostMapping("/{id}/fotos")
    public ResponseEntity<ApiResponse<ImovelFotoResponse>> addFoto(@PathVariable UUID id,
                                                                   @Valid @RequestBody ImovelFotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Foto cadastrada com sucesso", addImovelFoto.execute(id, request)));
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    public ResponseEntity<Void> removeFoto(@PathVariable UUID id, @PathVariable UUID fotoId) {
        removeImovelFoto.execute(id, fotoId);
        return ResponseEntity.noContent().build();
    }
}
