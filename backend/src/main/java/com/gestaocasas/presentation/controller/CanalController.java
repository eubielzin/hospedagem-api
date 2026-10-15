package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.CanalRequest;
import com.gestaocasas.application.dto.CanalResponse;
import com.gestaocasas.application.usecase.canal.ListCanaisUseCase;
import com.gestaocasas.application.usecase.canal.SalvarCanalUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/canais")
@Tag(name = "Canais", description = "Origem das reservas: recepção, Booking.com, Airbnb, Expedia...")
public class CanalController {

    private final ListCanaisUseCase listCanais;
    private final SalvarCanalUseCase salvarCanal;

    public CanalController(ListCanaisUseCase listCanais, SalvarCanalUseCase salvarCanal) {
        this.listCanais = listCanais;
        this.salvarCanal = salvarCanal;
    }

    @GetMapping
    @Operation(summary = "Lista canais em ordem alfabética", description = "Inativos só com incluirInativos=true.")
    public ResponseEntity<ApiResponse<List<CanalResponse>>> list(
            @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return ResponseEntity.ok(ApiResponse.ok("Canais recuperados com sucesso", listCanais.execute(incluirInativos)));
    }

    @PostMapping
    @Operation(summary = "Cadastra um canal", description = "Responde 409 se o nome já existir.")
    public ResponseEntity<ApiResponse<CanalResponse>> create(@Valid @RequestBody CanalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Canal criado com sucesso", salvarCanal.criar(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CanalResponse>> update(@PathVariable UUID id,
                                                             @Valid @RequestBody CanalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Canal atualizado com sucesso", salvarCanal.atualizar(id, request)));
    }

    @PatchMapping("/{id}/inativar")
    public ResponseEntity<ApiResponse<CanalResponse>> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Canal inativado com sucesso", salvarCanal.definirAtivo(id, false)));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<ApiResponse<CanalResponse>> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Canal reativado com sucesso", salvarCanal.definirAtivo(id, true)));
    }
}
