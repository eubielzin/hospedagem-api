package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.GovernancaRequest;
import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.application.usecase.uh.AlterarGovernancaUseCase;
import com.gestaocasas.application.usecase.uh.ListUhsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/uhs")
@Tag(name = "UHs e governança", description = "A UH é o imóvel: o id da UH é o id do imóvel.")
public class UhController {

    private final ListUhsUseCase listUhs;
    private final AlterarGovernancaUseCase alterarGovernanca;

    public UhController(ListUhsUseCase listUhs, AlterarGovernancaUseCase alterarGovernanca) {
        this.listUhs = listUhs;
        this.alterarGovernanca = alterarGovernanca;
    }

    @GetMapping
    @Operation(summary = "Lista as UHs com o status de governança",
            description = "Casas primeiro, depois o imóvel completo. OCUPADA é calculada pelas reservas hospedadas; "
                    + "com o imóvel completo hospedado, todas as casas saem OCUPADA.")
    public ResponseEntity<ApiResponse<List<UhResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("UHs recuperadas com sucesso", listUhs.execute()));
    }

    @PatchMapping("/{id}/governanca")
    @Operation(summary = "Define o status de governança",
            description = "Aceita LIVRE, SUJA ou MANUTENCAO. Responde 422 RN-07 se a UH estiver ocupada.")
    public ResponseEntity<ApiResponse<UhResponse>> governanca(@PathVariable UUID id,
                                                              @Valid @RequestBody GovernancaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Governança atualizada com sucesso",
                alterarGovernanca.execute(id, request.status())));
    }
}
