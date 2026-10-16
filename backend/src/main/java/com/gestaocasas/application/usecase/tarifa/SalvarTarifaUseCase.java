package com.gestaocasas.application.usecase.tarifa;

import com.gestaocasas.application.dto.TarifaRequest;
import com.gestaocasas.application.dto.TarifaResponse;
import com.gestaocasas.application.mapper.CategoriaUhMapper;
import com.gestaocasas.domain.entity.Tarifa;
import com.gestaocasas.domain.exception.CategoriaUhNotFoundException;
import com.gestaocasas.domain.exception.TarifaNotFoundException;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import com.gestaocasas.domain.repository.TarifaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF-31 e RF-32. Tarifas da mesma categoria podem se sobrepor: em cada noite vale a de período mais curto.
 * RN-11: valem para todos os canais.
 */
@Component
public class SalvarTarifaUseCase {

    private final TarifaRepository repository;
    private final CategoriaUhRepository categoriaRepository;
    private final CategoriaUhMapper mapper;

    public SalvarTarifaUseCase(TarifaRepository repository, CategoriaUhRepository categoriaRepository,
                               CategoriaUhMapper mapper) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.mapper = mapper;
    }

    @Transactional
    public TarifaResponse criar(TarifaRequest request) {
        Tarifa tarifa = new Tarifa();
        copiar(request, tarifa);
        return mapper.toResponse(repository.saveAndFlush(tarifa));
    }

    @Transactional
    public TarifaResponse atualizar(UUID id, TarifaRequest request) {
        Tarifa tarifa = repository.findById(id).orElseThrow(() -> new TarifaNotFoundException(id));
        copiar(request, tarifa);
        return mapper.toResponse(repository.saveAndFlush(tarifa));
    }

    private void copiar(TarifaRequest request, Tarifa tarifa) {
        tarifa.setCategoria(categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new CategoriaUhNotFoundException(request.categoriaId())));
        tarifa.setNome(request.nome().trim());
        tarifa.setInicio(request.inicio());
        tarifa.setFim(request.fim());
        tarifa.setValorDiaria(request.valorDiaria());
        tarifa.setEstadiaMinima(request.estadiaMinima());
        tarifa.setFechadoChegada(request.fechadoChegada());
        tarifa.setFechadoSaida(request.fechadoSaida());
        tarifa.setVendaFechada(request.vendaFechada());
    }
}
