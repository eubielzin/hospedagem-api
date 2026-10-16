package com.gestaocasas.infrastructure.demo;

import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Tarifa;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.TarifaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Dados sintéticos de demonstração (RN-16), iguais aos do mock do front: 3 categorias, 6 casas, o imóvel
 * completo e as tarifas. Só roda com {@code DEMO_SEED=true} e sem nenhum imóvel cadastrado, para nunca
 * misturar dados falsos com os reais.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DadosDemonstracao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);

    private final ImovelRepository imovelRepository;
    private final CategoriaUhRepository categoriaRepository;
    private final TarifaRepository tarifaRepository;
    private final Clock clock;

    public DadosDemonstracao(ImovelRepository imovelRepository, CategoriaUhRepository categoriaRepository,
                             TarifaRepository tarifaRepository, Clock clock) {
        this.imovelRepository = imovelRepository;
        this.categoriaRepository = categoriaRepository;
        this.tarifaRepository = tarifaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (imovelRepository.count() > 0) {
            log.info("Dados de demonstração ignorados: já há imóveis cadastrados");
            return;
        }
        CategoriaUh familia = categoria("Suíte Família Comfort", 2, "Casa para família, com 2 quartos.");
        CategoriaUh tripla = categoria("Suíte Tripla Comfort", 1, "Casa para até 3 adultos.");
        CategoriaUh completo = categoria("Imóvel completo", 3, "Todas as casas vendidas como uma unidade.");

        casa("Casa Alegria", familia, 4, 2, TipoUh.CASA);
        casa("Casa Amizade", familia, 4, 2, TipoUh.CASA);
        casa("Casa Essência", tripla, 3, 1, TipoUh.CASA);
        casa("Casa Flor", tripla, 3, 1, TipoUh.CASA);
        casa("Casa Sabedoria", familia, 4, 2, TipoUh.CASA);
        casa("Casa Serenidade", tripla, 3, 1, TipoUh.CASA);
        casa("Imóvel completo", completo, 20, 10, TipoUh.IMOVEL_COMPLETO);

        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioBase = hoje.minusYears(1).withDayOfYear(1);
        LocalDate fimBase = hoje.plusYears(1).withDayOfYear(1).plusYears(1).minusDays(1);
        tarifa(familia, "Tarifa base", inicioBase, fimBase, "480.00", 1);
        tarifa(tripla, "Tarifa base", inicioBase, fimBase, "380.00", 1);
        tarifa(completo, "Tarifa base", inicioBase, fimBase, "2600.00", 1);
        tarifa(familia, "Alta temporada", hoje.plusDays(25), hoje.plusDays(45), "620.00", 2);
        tarifa(tripla, "Alta temporada", hoje.plusDays(25), hoje.plusDays(45), "490.00", 2);
        tarifa(completo, "Alta temporada", hoje.plusDays(25), hoje.plusDays(45), "3400.00", 3);
        log.info("Dados de demonstração criados: 7 UHs, 3 categorias e 6 tarifas");
    }

    private CategoriaUh categoria(String nome, int nivel, String descricao) {
        CategoriaUh categoria = new CategoriaUh();
        categoria.setNome(nome);
        categoria.setNivel(nivel);
        categoria.setDescricao(descricao);
        categoria.setAtiva(true);
        return categoriaRepository.save(categoria);
    }

    private void casa(String nome, CategoriaUh categoria, int adultos, int criancas, TipoUh tipo) {
        Imovel imovel = new Imovel();
        imovel.setNome(nome);
        imovel.setTipo(tipo);
        imovel.setCategoria(categoria);
        imovel.setCapacidadeAdultos(adultos);
        imovel.setCapacidadeCriancas(criancas);
        imovel.setLogradouro("Rua das Dunas");
        imovel.setNumero(tipo == TipoUh.CASA ? null : "s/n");
        imovel.setBairro("Centro");
        imovel.setCidade("Barreirinhas");
        imovel.setUf("MA");
        imovel.setQuartos(tipo == TipoUh.CASA ? 2 : 12);
        imovel.setBanheiros(tipo == TipoUh.CASA ? 1 : 6);
        imovel.setCamas(adultos);
        imovel.setValorBaseDiaria(BigDecimal.ZERO);
        imovel.setStatus(StatusImovel.ATIVO);
        imovel.setStatusGovernanca(StatusGovernanca.LIVRE);
        imovelRepository.save(imovel);
    }

    private void tarifa(CategoriaUh categoria, String nome, LocalDate inicio, LocalDate fim, String valor,
                        int estadiaMinima) {
        Tarifa tarifa = new Tarifa();
        tarifa.setCategoria(categoria);
        tarifa.setNome(nome);
        tarifa.setInicio(inicio);
        tarifa.setFim(fim);
        tarifa.setValorDiaria(new BigDecimal(valor));
        tarifa.setEstadiaMinima(estadiaMinima);
        tarifaRepository.save(tarifa);
    }
}
