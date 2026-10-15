package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.CancelamentoReservaRequest;
import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.usecase.reserva.*;
import com.gestaocasas.domain.enums.StatusReserva;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reservas")
@Tag(name = "Reservas", description = "A UH da reserva é o imóvel (uhId = id do imóvel). Período [checkin, checkout): "
        + "a noite do checkout não é ocupada.")
public class ReservaController {

    private final ListReservasUseCase listReservas;
    private final GetReservaByIdUseCase getReservaById;
    private final CreateReservaUseCase createReserva;
    private final UpdateReservaUseCase updateReserva;
    private final AlterarStatusReservaUseCase alterarStatus;

    public ReservaController(ListReservasUseCase listReservas, GetReservaByIdUseCase getReservaById,
                             CreateReservaUseCase createReserva, UpdateReservaUseCase updateReserva,
                             AlterarStatusReservaUseCase alterarStatus) {
        this.listReservas = listReservas;
        this.getReservaById = getReservaById;
        this.createReserva = createReserva;
        this.updateReserva = updateReserva;
        this.alterarStatus = alterarStatus;
    }

    @GetMapping
    @Operation(summary = "Lista reservas, da entrada mais recente para a mais antiga",
            description = "busca procura no nome do hóspede, no código ou no nome da UH. Com inicio e/ou fim, "
                    + "devolve as reservas cujo período cruza o intervalo.")
    public ResponseEntity<ApiResponse<List<ReservaResponse>>> list(
            @RequestParam(required = false) @Size(max = 100, message = "Busca deve ter no máximo 100 caracteres")
            String busca,
            @RequestParam(required = false) StatusReserva status,
            @RequestParam(required = false) UUID canalId,
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Reservas recuperadas com sucesso",
                listReservas.execute(busca, status, canalId, inicio, fim)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservaResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva encontrada", getReservaById.execute(id)));
    }

    @PostMapping
    @Operation(summary = "Cria uma reserva com status RESERVADO",
            description = "Responde 409 se a UH já estiver reservada no período e 422 se a UH não estiver ativa, "
                    + "a lotação passar da capacidade ou o hóspede ou canal estiver inativo.")
    public ResponseEntity<ApiResponse<ReservaResponse>> create(@Valid @RequestBody ReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reserva criada com sucesso", createReserva.execute(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita uma reserva", description = "Só reservas com status RESERVADO.")
    public ResponseEntity<ApiResponse<ReservaResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ReservaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva atualizada com sucesso", updateReserva.execute(id, request)));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "Faz o check-in", description = "Só de reservas RESERVADO, a partir da data de entrada, e "
            + "com o documento do titular preenchido (RN-04).")
    public ResponseEntity<ApiResponse<ReservaResponse>> checkin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Check-in realizado com sucesso", alterarStatus.checkin(id)));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Faz o check-out", description = "Só de reservas HOSPEDADO.")
    public ResponseEntity<ApiResponse<ReservaResponse>> checkout(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Check-out realizado com sucesso", alterarStatus.checkout(id)));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancela uma reserva", description = "Só reservas RESERVADO. O corpo, com o motivo, é "
            + "opcional. A UH fica livre no período.")
    public ResponseEntity<ApiResponse<ReservaResponse>> cancelar(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CancelamentoReservaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada com sucesso", alterarStatus.cancelar(id, request)));
    }
}
