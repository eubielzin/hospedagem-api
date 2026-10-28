package com.gestaocasas.application.usecase.operacao;

import com.gestaocasas.application.dto.ConfiguracaoRequest;
import com.gestaocasas.application.dto.ConfiguracaoResponse;
import com.gestaocasas.domain.entity.Configuracao;
import com.gestaocasas.domain.repository.ConfiguracaoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RF-67: configurações da pousada, uma linha só. Os horários são o padrão dos imóveis sem horário próprio. */
@Component
public class ConfiguracaoUseCase {

    private final ConfiguracaoRepository repository;

    public ConfiguracaoUseCase(ConfiguracaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoResponse buscar() {
        return toResponse(configuracao());
    }

    @Transactional
    public ConfiguracaoResponse atualizar(ConfiguracaoRequest request) {
        Configuracao configuracao = configuracao();
        configuracao.setNomePousada(request.nomePousada().trim());
        configuracao.setCidade(request.cidade().trim());
        configuracao.setHorarioCheckin(request.horarioCheckin());
        configuracao.setHorarioCheckout(request.horarioCheckout());
        configuracao.setIdadeLimiteCrianca(request.idadeLimiteCrianca());
        configuracao.setPoliticaCancelamento(request.politicaCancelamento().trim());
        configuracao.setFormasPagamento(request.formasPagamento());
        return toResponse(repository.saveAndFlush(configuracao));
    }

    private Configuracao configuracao() {
        return repository.findById(Configuracao.ID)
                .orElseThrow(() -> new IllegalStateException("A configuração da pousada não existe"));
    }

    private static ConfiguracaoResponse toResponse(Configuracao c) {
        return new ConfiguracaoResponse(c.getNomePousada(), c.getCidade(), c.getHorarioCheckin(), c.getHorarioCheckout(),
                c.getIdadeLimiteCrianca(), c.getPoliticaCancelamento(), c.getFormasPagamento());
    }
}
