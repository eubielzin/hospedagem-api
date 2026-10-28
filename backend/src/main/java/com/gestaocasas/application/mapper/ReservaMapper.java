package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.entity.ReservaEvento;
import com.gestaocasas.domain.enums.StatusReserva;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReservaMapper {

    /** Detalhe, com o histórico. */
    @Mapping(target = "uhId", source = "imovel.id")
    @Mapping(target = "uhNome", source = "imovel.nome")
    @Mapping(target = "titularId", source = "titular.id")
    @Mapping(target = "titularNome", source = "titular.nome")
    @Mapping(target = "canalId", source = "canal.id")
    @Mapping(target = "canalNome", source = "canal.nome")
    @Mapping(target = "criadaEm", source = "criadoEm")
    @Mapping(target = "motivoCancelamento", expression = "java(cancelada(reserva) ? reserva.getEncerramento().getMotivo() : null)")
    @Mapping(target = "canceladaEm", expression = "java(cancelada(reserva) ? reserva.getEncerradaEm() : null)")
    ReservaResponse toResponse(Reserva reserva);

    /** Listagens: sem o histórico, que fica nulo. */
    @Named("resumo")
    @Mapping(target = "uhId", source = "imovel.id")
    @Mapping(target = "uhNome", source = "imovel.nome")
    @Mapping(target = "titularId", source = "titular.id")
    @Mapping(target = "titularNome", source = "titular.nome")
    @Mapping(target = "canalId", source = "canal.id")
    @Mapping(target = "canalNome", source = "canal.nome")
    @Mapping(target = "criadaEm", source = "criadoEm")
    @Mapping(target = "motivoCancelamento", expression = "java(cancelada(reserva) ? reserva.getEncerramento().getMotivo() : null)")
    @Mapping(target = "canceladaEm", expression = "java(cancelada(reserva) ? reserva.getEncerradaEm() : null)")
    @Mapping(target = "historico", ignore = true)
    ReservaResponse toResumo(Reserva reserva);

    @Mapping(target = "usuario", source = "usuario.nome")
    ReservaResponse.EventoHistorico toEvento(ReservaEvento evento);

    default boolean cancelada(Reserva reserva) {
        return reserva.getStatus() == StatusReserva.CANCELADA && reserva.getEncerramento() != null;
    }
}
