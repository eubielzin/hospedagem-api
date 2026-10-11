package com.gestaocasas;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusImovel;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class ImovelFixtures {

    private ImovelFixtures() {
    }

    public static ImovelRequest request(StatusImovel status) {
        return new ImovelRequest("Casa da Praia", "Casa pé na areia", "Rua das Conchas", "120", null, "Centro",
                "Ubatuba", "sp", "11680-000", 8, 3, 2, 5, new BigDecimal("450.00"),
                LocalTime.of(15, 0), LocalTime.of(11, 0), status,
                new LinkedHashSet<>(List.of("Wi-Fi", " Piscina ")));
    }

    public static Imovel imovel(UUID id, StatusImovel status) {
        Imovel imovel = new Imovel();
        imovel.setId(id);
        imovel.setNome("Casa Antiga");
        imovel.setLogradouro("Rua A");
        imovel.setCidade("Santos");
        imovel.setUf("SP");
        imovel.setCapacidadeMaxima(4);
        imovel.setQuartos(2);
        imovel.setBanheiros(1);
        imovel.setCamas(3);
        imovel.setValorBaseDiaria(new BigDecimal("200.00"));
        imovel.setHorarioCheckin(LocalTime.of(14, 0));
        imovel.setHorarioCheckout(LocalTime.of(10, 0));
        imovel.setStatus(status);
        imovel.getComodidades().add("Churrasqueira");
        return imovel;
    }
}
