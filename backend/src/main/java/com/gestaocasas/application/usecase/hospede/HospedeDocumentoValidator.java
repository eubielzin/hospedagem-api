package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.domain.enums.TipoDocumento;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Um documento pertence a um único hóspede, ativo ou inativo. */
@Component
public class HospedeDocumentoValidator {

    private final HospedeRepository repository;

    public HospedeDocumentoValidator(HospedeRepository repository) {
        this.repository = repository;
    }

    /**
     * Chamado antes de alterar a entidade: a consulta não pode encontrar a alteração ainda pendente.
     *
     * @param ignorarId o próprio hóspede, na edição; null no cadastro
     */
    void validar(TipoDocumento tipo, String numero, UUID ignorarId) {
        if (numero.isEmpty()) {
            return;
        }
        repository.findByDocumento(tipo, numero)
                .filter(existente -> !existente.getId().equals(ignorarId))
                .ifPresent(existente -> {
                    throw new ConflitoException(
                            "Já existe um hóspede com este documento (" + existente.getNome() + ").");
                });
    }
}
