package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.LancamentoResponse;
import com.gestaocasas.application.dto.PagamentoResponse;
import com.gestaocasas.domain.entity.LancamentoConta;
import com.gestaocasas.domain.entity.Pagamento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PagamentoMapper {

    @Mapping(target = "reservaId", source = "reserva.id")
    @Mapping(target = "vendaId", source = "venda.id")
    @Mapping(target = "usuario", source = "usuario.nome")
    PagamentoResponse toResponse(Pagamento pagamento);

    @Mapping(target = "reservaId", source = "reserva.id")
    LancamentoResponse toResponse(LancamentoConta lancamento);
}
