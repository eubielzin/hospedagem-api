package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.Configuracao;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.repository.ConfiguracaoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/** Horário efetivo do imóvel: o próprio, ou o padrão da pousada quando ele não tem um. */
@Component
public class HorariosPadrao {

    private final ConfiguracaoRepository repository;

    public HorariosPadrao(ConfiguracaoRepository repository) {
        this.repository = repository;
    }

    public LocalTime checkin(Imovel imovel) {
        return imovel.getHorarioCheckin() != null ? imovel.getHorarioCheckin() : configuracao().getHorarioCheckin();
    }

    public LocalTime checkout(Imovel imovel) {
        return imovel.getHorarioCheckout() != null ? imovel.getHorarioCheckout() : configuracao().getHorarioCheckout();
    }

    private Configuracao configuracao() {
        return repository.findById(Configuracao.ID)
                .orElseThrow(() -> new IllegalStateException("A configuração da pousada não existe"));
    }
}
