package com.gestaocasas;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.application.mapper.ImovelMapperImpl;
import com.gestaocasas.application.service.HorariosPadrao;
import com.gestaocasas.domain.entity.Configuracao;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.repository.ConfiguracaoRepository;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class ImovelFixtures {

    private ImovelFixtures() {
    }

    public static ImovelRequest request(StatusImovel status) {
        return new ImovelRequest("Casa da Praia", "Casa pé na areia", "Rua das Conchas", "120", null, "Centro",
                "Ubatuba", "sp", "11680-000", null, null, 6, 2, null, 3, 2, 5, new BigDecimal("450.00"),
                LocalTime.of(15, 0), LocalTime.of(11, 0), status,
                new LinkedHashSet<>(List.of("Wi-Fi", " Piscina ")));
    }

    /** Forma antiga do front em produção: só a capacidade total, sem os campos da UH. */
    public static ImovelRequest requestAntigo(StatusImovel status, int capacidadeMaxima) {
        return new ImovelRequest("Casa da Praia", null, "Rua das Conchas", "120", null, "Centro", "Ubatuba", "SP",
                "11680-000", null, null, null, null, capacidadeMaxima, 3, 2, 5, new BigDecimal("450.00"),
                LocalTime.of(15, 0), LocalTime.of(11, 0), status, null);
    }

    /** UH com tipo e categoria; logradouro nulo deixa o imóvel completo receber o endereço da pousada. */
    public static ImovelRequest requestUh(TipoUh tipo, UUID categoriaId, String logradouro) {
        return new ImovelRequest("Imóvel completo", null, logradouro, null, null, null,
                logradouro == null ? null : "Barreirinhas", logradouro == null ? null : "MA", null,
                tipo, categoriaId, 20, 10, null, 12, 6, 20, new BigDecimal("2600.00"), null, null, null, null);
    }

    public static Imovel imovel(UUID id, StatusImovel status) {
        Imovel imovel = new Imovel();
        imovel.setId(id);
        imovel.setNome("Casa Antiga");
        imovel.setLogradouro("Rua A");
        imovel.setCidade("Santos");
        imovel.setUf("SP");
        imovel.setTipo(TipoUh.CASA);
        imovel.setCapacidadeAdultos(4);
        imovel.setCapacidadeCriancas(0);
        imovel.setQuartos(2);
        imovel.setBanheiros(1);
        imovel.setCamas(3);
        imovel.setValorBaseDiaria(new BigDecimal("200.00"));
        imovel.setHorarioCheckin(LocalTime.of(14, 0));
        imovel.setHorarioCheckout(LocalTime.of(10, 0));
        imovel.setStatus(status);
        imovel.setStatusGovernanca(StatusGovernanca.LIVRE);
        imovel.getComodidades().add("Churrasqueira");
        return imovel;
    }

    /** Mapper real, com o horário padrão da pousada em 14:00 e 12:00. */
    public static ImovelMapper mapper() {
        Configuracao configuracao = new Configuracao();
        configuracao.setHorarioCheckin(LocalTime.of(14, 0));
        configuracao.setHorarioCheckout(LocalTime.of(12, 0));
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        lenient().when(repository.findById(Configuracao.ID)).thenReturn(Optional.of(configuracao));
        ImovelMapperImpl mapper = new ImovelMapperImpl();
        mapper.setHorariosPadrao(new HorariosPadrao(repository));
        return mapper;
    }
}
