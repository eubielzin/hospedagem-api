package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.repository.ReservaRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class ListReservasUseCase {

    private final ReservaRepository repository;
    private final ReservaMapper mapper;

    public ListReservasUseCase(ReservaRepository repository, ReservaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * @param busca  parte do nome do hóspede, do código (CM-1546 ou 1546) ou do nome da UH
     * @param inicio com fim, devolve as reservas cujo período cruza [inicio, fim]
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ReservaResponse> execute(String busca, StatusReserva status, UUID canalId,
                                         LocalDate inicio, LocalDate fim) {
        String termo = busca == null ? "" : busca.trim();
        Specification<Reserva> filtro = (root, query, cb) -> {
            // Fetch como join: os nomes vêm na mesma consulta e servem também para a busca.
            Join<Reserva, Imovel> imovel = (Join<Reserva, Imovel>) root.<Reserva, Imovel>fetch("imovel");
            Join<Reserva, Hospede> titular = (Join<Reserva, Hospede>) root.<Reserva, Hospede>fetch("titular");
            Join<Reserva, Canal> canal = (Join<Reserva, Canal>) root.<Reserva, Canal>fetch("canal");

            List<jakarta.persistence.criteria.Predicate> condicoes = new ArrayList<>();
            if (status != null) {
                condicoes.add(cb.equal(root.get("status"), status));
            }
            if (canalId != null) {
                condicoes.add(cb.equal(canal.get("id"), canalId));
            }
            if (inicio != null) {
                condicoes.add(cb.greaterThanOrEqualTo(root.get("checkout"), inicio));
            }
            if (fim != null) {
                condicoes.add(cb.lessThanOrEqualTo(root.get("checkin"), fim));
            }
            if (!termo.isEmpty()) {
                Expression<String> padrao = cb.concat(cb.concat("%", normalizar(cb, cb.literal(escapeLike(termo)))), "%");
                condicoes.add(cb.or(
                        cb.like(normalizar(cb, titular.get("nome")), padrao, '!'),
                        cb.like(normalizar(cb, imovel.get("nome")), padrao, '!'),
                        cb.like(cb.lower(root.get("codigo")),
                                "%" + escapeLike(termo.toLowerCase(Locale.ROOT)) + "%", '!')));
            }
            return cb.and(condicoes.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return repository.findAll(filtro, Sort.by(Sort.Order.desc("checkin"), Sort.Order.desc("codigo"))).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Mesma normalização da busca de produtos e hóspedes (V7): sem acentos e em minúsculas.
    private static Expression<String> normalizar(CriteriaBuilder cb, Expression<String> texto) {
        return cb.function("normalizar_busca", String.class, texto);
    }

    private static String escapeLike(String valor) {
        return valor.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
