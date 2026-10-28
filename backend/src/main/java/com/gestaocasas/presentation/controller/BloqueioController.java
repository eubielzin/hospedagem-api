package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.BloqueioRequest;
import com.gestaocasas.application.dto.BloqueioResponse;
import com.gestaocasas.application.usecase.bloqueio.GerenciarBloqueioUseCase;
import com.gestaocasas.application.usecase.bloqueio.ListBloqueiosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bloqueios")
@Tag(name = "Bloqueios", description = "UH indisponível de inicio a fim (inclusive), por manutenção, uso próprio etc. "
        + "Nunca excluído: encerrado (RN-15).")
public class BloqueioController {

    private final GerenciarBloqueioUseCase gerenciarBloqueio;
    private final ListBloqueiosUseCase listBloqueios;

    public BloqueioController(GerenciarBloqueioUseCase gerenciarBloqueio, ListBloqueiosUseCase listBloqueios) {
        this.gerenciarBloqueio = gerenciarBloqueio;
        this.listBloqueios = listBloqueios;
    }

    @GetMapping
    @Operation(summary = "Lista os bloqueios ativos que cruzam o período",
            description = "inicio e fim inclusive; sem eles, todos os ativos. Ordem: início.")
    public ResponseEntity<ApiResponse<List<BloqueioResponse>>> list(@RequestParam(required = false) LocalDate inicio,
                                                                    @RequestParam(required = false) LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Bloqueios recuperados com sucesso", listBloqueios.execute(inicio, fim)));
    }

    @PostMapping
    @Operation(summary = "Bloqueia uma UH",
            description = "422 RN-01 se a UH tiver reserva ativa ou outro bloqueio no período; RN-02 se o conflito "
                    + "for no imóvel completo ou numa casa dele; RN-15 se a UH estiver inativa.")
    public ResponseEntity<ApiResponse<BloqueioResponse>> create(@Valid @RequestBody BloqueioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bloqueio criado com sucesso", gerenciarBloqueio.criar(request)));
    }

    @PatchMapping("/{id}/encerrar")
    @Operation(summary = "Encerra um bloqueio", description = "A UH volta a ficar disponível. Encerrar de novo não muda nada.")
    public ResponseEntity<ApiResponse<BloqueioResponse>> encerrar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Bloqueio encerrado com sucesso", gerenciarBloqueio.encerrar(id)));
    }
}
