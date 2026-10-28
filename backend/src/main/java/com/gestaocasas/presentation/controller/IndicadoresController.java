package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.IndicadoresResponse;
import com.gestaocasas.application.dto.PrevisaoResponse;
import com.gestaocasas.application.usecase.indicadores.IndicadoresUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/indicadores")
@Tag(name = "Indicadores", description = "Módulo 8. Noites por casa (o imóvel completo vale uma em cada casa ativa); "
        + "receita de hospedagem apropriada noite a noite; canceladas e no-show não ocupam.")
public class IndicadoresController {

    private final IndicadoresUseCase indicadores;

    public IndicadoresController(IndicadoresUseCase indicadores) {
        this.indicadores = indicadores;
    }

    @GetMapping
    @Operation(summary = "Indicadores do período", description = "inicio e fim inclusive, até 731 dias. anterior é o "
            + "mesmo número de dias imediatamente antes de inicio.")
    public ResponseEntity<ApiResponse<IndicadoresResponse>> indicadores(@RequestParam LocalDate inicio,
                                                                        @RequestParam LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.ok("Indicadores calculados", indicadores.indicadores(inicio, fim)));
    }

    @GetMapping("/previsao")
    @Operation(summary = "Previsão on the books", description = "A partir de inicio, por dias (1 a 366, padrão 90).")
    public ResponseEntity<ApiResponse<PrevisaoResponse>> previsao(@RequestParam LocalDate inicio,
                                                                  @RequestParam(defaultValue = "90") int dias) {
        return ResponseEntity.ok(ApiResponse.ok("Previsão calculada", indicadores.previsao(inicio, dias)));
    }
}
