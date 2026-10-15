package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.usecase.hospede.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hospedes")
@Tag(name = "Hóspedes", description = "Registros nunca são excluídos: são inativados, mantendo o histórico (RN-15).")
public class HospedeController {

    private final ListHospedesUseCase listHospedes;
    private final GetHospedeByIdUseCase getHospedeById;
    private final CreateHospedeUseCase createHospede;
    private final UpdateHospedeUseCase updateHospede;
    private final InativarHospedeUseCase inativarHospede;
    private final ReativarHospedeUseCase reativarHospede;
    private final HistoricoHospedeUseCase historicoHospede;

    public HospedeController(ListHospedesUseCase listHospedes, GetHospedeByIdUseCase getHospedeById,
                             CreateHospedeUseCase createHospede, UpdateHospedeUseCase updateHospede,
                             InativarHospedeUseCase inativarHospede, ReativarHospedeUseCase reativarHospede,
                             HistoricoHospedeUseCase historicoHospede) {
        this.listHospedes = listHospedes;
        this.getHospedeById = getHospedeById;
        this.createHospede = createHospede;
        this.updateHospede = updateHospede;
        this.inativarHospede = inativarHospede;
        this.reativarHospede = reativarHospede;
        this.historicoHospede = historicoHospede;
    }

    @GetMapping
    @Operation(summary = "Lista hóspedes em ordem alfabética",
            description = "busca procura em parte do nome (sem diferenciar maiúsculas nem acentos) ou em parte "
                    + "dos dígitos do documento. Sem busca, lista todos. Inativos só com incluirInativos=true.")
    public ResponseEntity<ApiResponse<List<HospedeResponse>>> list(
            @RequestParam(required = false) @Size(max = 100, message = "Busca deve ter no máximo 100 caracteres")
            String busca,
            @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return ResponseEntity.ok(ApiResponse.ok("Hóspedes recuperados com sucesso",
                listHospedes.execute(busca, incluirInativos)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HospedeResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Hóspede encontrado", getHospedeById.execute(id)));
    }

    @PostMapping
    @Operation(summary = "Cadastra um hóspede", description = "O documento é opcional no cadastro. Responde 409 "
            + "se o mesmo tipo e número de documento já pertencer a outro hóspede.")
    public ResponseEntity<ApiResponse<HospedeResponse>> create(@Valid @RequestBody HospedeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Hóspede criado com sucesso", createHospede.execute(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um hóspede", description = "Não altera a situação (ativo).")
    public ResponseEntity<ApiResponse<HospedeResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody HospedeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Hóspede atualizado com sucesso", updateHospede.execute(id, request)));
    }

    @PatchMapping("/{id}/inativar")
    public ResponseEntity<ApiResponse<HospedeResponse>> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Hóspede inativado com sucesso", inativarHospede.execute(id)));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<ApiResponse<HospedeResponse>> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Hóspede reativado com sucesso", reativarHospede.execute(id)));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Reservas em que o hóspede é o titular (RF-27)",
            description = "Da entrada mais recente para a mais antiga, inclusive canceladas.")
    public ResponseEntity<ApiResponse<List<ReservaResponse>>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Histórico recuperado com sucesso", historicoHospede.execute(id)));
    }
}
