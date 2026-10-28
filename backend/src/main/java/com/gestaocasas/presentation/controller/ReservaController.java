package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.CancelamentoReservaRequest;
import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.application.dto.EncerramentoRequest;
import com.gestaocasas.application.dto.FnrhRequest;
import com.gestaocasas.application.dto.RemanejamentoRequest;
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
        + "a noite do checkout não é ocupada. Ação em status errado: 422 RN-03.")
public class ReservaController {

    private final ListReservasUseCase listReservas;
    private final GetReservaByIdUseCase getReservaById;
    private final CreateReservaUseCase createReserva;
    private final UpdateReservaUseCase updateReserva;
    private final AlterarStatusReservaUseCase alterarStatus;
    private final EncerrarReservaUseCase encerrarReserva;
    private final RemanejarReservaUseCase remanejarReserva;
    private final PreencherFnrhUseCase preencherFnrh;
    private final CotarReservaUseCase cotarReserva;

    public ReservaController(ListReservasUseCase listReservas, GetReservaByIdUseCase getReservaById,
                             CreateReservaUseCase createReserva, UpdateReservaUseCase updateReserva,
                             AlterarStatusReservaUseCase alterarStatus, EncerrarReservaUseCase encerrarReserva,
                             RemanejarReservaUseCase remanejarReserva, PreencherFnrhUseCase preencherFnrh,
                             CotarReservaUseCase cotarReserva) {
        this.listReservas = listReservas;
        this.getReservaById = getReservaById;
        this.createReserva = createReserva;
        this.updateReserva = updateReserva;
        this.alterarStatus = alterarStatus;
        this.encerrarReserva = encerrarReserva;
        this.remanejarReserva = remanejarReserva;
        this.preencherFnrh = preencherFnrh;
        this.cotarReserva = cotarReserva;
    }

    @GetMapping
    @Operation(summary = "Lista reservas, da entrada mais recente para a mais antiga",
            description = "busca procura no nome do hóspede, no código, no código externo ou no nome da UH. Com "
                    + "inicio e/ou fim, devolve as reservas com checkout > inicio e checkin <= fim. Sem histórico.")
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

    @GetMapping("/cotacao")
    @Operation(summary = "Cota uma estadia pelas tarifas (RF-31, RF-32)",
            description = "Em cada noite vale a tarifa de período mais curto da categoria da UH. 400 se a saída não "
                    + "for depois da entrada; 422 RF-31 sem tarifa; 422 RF-32 venda fechada, chegada ou saída "
                    + "fechada ou estadia mínima. Não confere disponibilidade.")
    public ResponseEntity<ApiResponse<CotacaoResponse>> cotacao(@RequestParam UUID uhId,
                                                                @RequestParam LocalDate checkin,
                                                                @RequestParam LocalDate checkout) {
        return ResponseEntity.ok(ApiResponse.ok("Cotação calculada", cotarReserva.execute(uhId, checkin, checkout)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da reserva, com o histórico")
    public ResponseEntity<ApiResponse<ReservaResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva encontrada", getReservaById.execute(id)));
    }

    @PostMapping
    @Operation(summary = "Cria uma reserva com status RESERVADO",
            description = "O valor vem da cotação (valorTotal do corpo é ignorado). Ordem das recusas: UH inativa "
                    + "(422 RN-15) ou em manutenção (422 RF-35), titular inativo (400), cotação (RF-31/RF-32), "
                    + "capacidade (422 RN-13) e disponibilidade (422 RN-01 na mesma UH, RN-02 no imóvel completo "
                    + "ou numa casa dele).")
    public ResponseEntity<ApiResponse<ReservaResponse>> create(@Valid @RequestBody ReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reserva criada com sucesso", createReserva.execute(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita uma reserva", description = "Só reservas RESERVADO; o valor é recotado.")
    public ResponseEntity<ApiResponse<ReservaResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ReservaRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva atualizada com sucesso", updateReserva.execute(id, request)));
    }

    @PutMapping("/{id}/remanejamento")
    @Operation(summary = "Troca a UH e/ou as datas (RF-06)",
            description = "RESERVADO ou HOSPEDADO; hospedado não muda a entrada (422 RN-03). Recota e confere "
                    + "capacidade e disponibilidade. Troca de casa com o hóspede dentro: a antiga fica SUJA.")
    public ResponseEntity<ApiResponse<ReservaResponse>> remanejar(@PathVariable UUID id,
                                                                  @Valid @RequestBody RemanejamentoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva remanejada com sucesso", remanejarReserva.execute(id, request)));
    }

    @PutMapping("/{id}/fnrh")
    @Operation(summary = "Preenche a FNRH (RF-23)", description = "RESERVADO ou HOSPEDADO; emitidaEm é hoje.")
    public ResponseEntity<ApiResponse<ReservaResponse>> fnrh(@PathVariable UUID id,
                                                             @Valid @RequestBody FnrhRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("FNRH preenchida com sucesso", preencherFnrh.execute(id, request)));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "Faz o check-in", description = "Só de reservas RESERVADO, a partir da data de entrada. "
            + "422 RN-04 sem documento do titular ou sem FNRH; 422 RF-35 com a UH suja ou em manutenção.")
    public ResponseEntity<ApiResponse<ReservaResponse>> checkin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Check-in realizado com sucesso", alterarStatus.checkin(id)));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Faz o check-out", description = "Só de reservas HOSPEDADO. A UH fica SUJA (RN-07).")
    public ResponseEntity<ApiResponse<ReservaResponse>> checkout(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Check-out realizado com sucesso", alterarStatus.checkout(id)));
    }

    @PostMapping("/{id}/cancelamento")
    @Operation(summary = "Cancela uma reserva", description = "Só RESERVADO; motivo obrigatório; multa, valorRetido e "
            + "valorDevolver opcionais (zero). A UH fica livre no período.")
    public ResponseEntity<ApiResponse<ReservaResponse>> cancelamento(@PathVariable UUID id,
                                                                     @Valid @RequestBody EncerramentoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada com sucesso", encerrarReserva.cancelar(id, request)));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancela uma reserva (rota antiga)",
            description = "Igual a /cancelamento, mas o corpo e o motivo são opcionais.")
    public ResponseEntity<ApiResponse<ReservaResponse>> cancelar(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CancelamentoReservaRequest request) {
        CancelamentoReservaRequest corpo = request == null ? new CancelamentoReservaRequest(null) : request;
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada com sucesso",
                encerrarReserva.cancelar(id, corpo.paraEncerramento())));
    }

    @PostMapping("/{id}/no-show")
    @Operation(summary = "Registra o no-show", description = "Só RESERVADO; mesmo corpo do cancelamento. A UH fica livre.")
    public ResponseEntity<ApiResponse<ReservaResponse>> noShow(@PathVariable UUID id,
                                                               @Valid @RequestBody EncerramentoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("No-show registrado com sucesso", encerrarReserva.noShow(id, request)));
    }
}
