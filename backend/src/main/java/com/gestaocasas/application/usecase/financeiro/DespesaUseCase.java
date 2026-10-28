package com.gestaocasas.application.usecase.financeiro;

import com.gestaocasas.application.dto.DespesaPagamentoRequest;
import com.gestaocasas.application.dto.DespesaRequest;
import com.gestaocasas.application.dto.DespesaResponse;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Despesa;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ResourceNotFoundException;
import com.gestaocasas.domain.repository.DespesaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** RF-45: contas a pagar. Nunca excluídas (RN-15). */
@Component
public class DespesaUseCase {

    private static final LocalDate SEM_INICIO = LocalDate.of(1900, 1, 1);
    private static final LocalDate SEM_FIM = LocalDate.of(9999, 12, 31);

    private final DespesaRepository repository;
    private final UsuarioLogado usuarioLogado;

    public DespesaUseCase(DespesaRepository repository, UsuarioLogado usuarioLogado) {
        this.repository = repository;
        this.usuarioLogado = usuarioLogado;
    }

    /** Por vencimento; inicio e fim (inclusive) filtram o vencimento. */
    @Transactional(readOnly = true)
    public List<DespesaResponse> listar(LocalDate inicio, LocalDate fim) {
        return repository.findPorVencimento(inicio == null ? SEM_INICIO : inicio, fim == null ? SEM_FIM : fim).stream()
                .map(DespesaUseCase::toResponse)
                .toList();
    }

    @Transactional
    public DespesaResponse criar(DespesaRequest request) {
        Despesa despesa = new Despesa();
        despesa.setCategoria(request.categoria().trim());
        despesa.setFornecedor(request.fornecedor().trim());
        despesa.setDescricao(request.descricao() == null || request.descricao().isBlank() ? null : request.descricao().trim());
        despesa.setValor(request.valor());
        despesa.setVencimento(request.vencimento());
        despesa.setUsuario(usuarioLogado.atual().orElse(null));
        return toResponse(repository.saveAndFlush(despesa));
    }

    @Transactional
    public DespesaResponse pagar(UUID id, DespesaPagamentoRequest request) {
        Despesa despesa = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada: " + id));
        if (despesa.getDataPagamento() != null) {
            throw new RegraDeNegocioException("Esta despesa já foi paga em " + Formato.data(despesa.getDataPagamento())
                    + ".");
        }
        despesa.setDataPagamento(request.data());
        despesa.setComprovante(request.comprovante() == null || request.comprovante().isBlank() ? null
                : request.comprovante().trim());
        return toResponse(repository.saveAndFlush(despesa));
    }

    private static DespesaResponse toResponse(Despesa d) {
        return new DespesaResponse(d.getId(), d.getCategoria(), d.getFornecedor(), d.getDescricao(), d.getValor(),
                d.getVencimento(), d.getDataPagamento(), d.getComprovante());
    }
}
