package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.TarifaRequest;
import com.gestaocasas.application.dto.TarifaResponse;
import com.gestaocasas.application.usecase.tarifa.ListTarifasUseCase;
import com.gestaocasas.application.usecase.tarifa.SalvarTarifaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tarifas")
@Tag(name = "Tarifas", description = "Diária por categoria e período (RF-31) com as restrições de venda (RF-32). "
        + "Tarifas podem se sobrepor: em cada noite vale a de período mais curto.")
public class TarifaController {

    private final ListTarifasUseCase listTarifas;
    private final SalvarTarifaUseCase salvarTarifa;

    public TarifaController(ListTarifasUseCase listTarifas, SalvarTarifaUseCase salvarTarifa) {
        this.listTarifas = listTarifas;
        this.salvarTarifa = salvarTarifa;
    }

    @GetMapping
    @Operation(summary = "Lista tarifas por início")
    public ResponseEntity<ApiResponse<List<TarifaResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Tarifas recuperadas com sucesso", listTarifas.execute()));
    }

    @PostMapping
    @Operation(summary = "Cadastra uma tarifa",
            description = "fim é a última noite (inclusive). 400 se fim < inicio, valorDiaria <= 0 ou estadiaMinima < 1.")
    public ResponseEntity<ApiResponse<TarifaResponse>> create(@Valid @RequestBody TarifaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tarifa criada com sucesso", salvarTarifa.criar(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TarifaResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody TarifaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tarifa atualizada com sucesso", salvarTarifa.atualizar(id, request)));
    }
}
