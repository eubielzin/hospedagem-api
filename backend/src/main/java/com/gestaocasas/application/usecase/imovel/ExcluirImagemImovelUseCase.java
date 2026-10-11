package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StorageProvider;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Banco e Storage não compartilham transação, então a exclusão de um arquivo acontece em três passos:
 * marca a foto como "exclusão pendente" (ela some das consultas), remove o objeto do Storage e só então
 * apaga o registro. Se o Storage falhar, o registro permanece com o caminho do objeto e repetir o
 * DELETE conclui a operação.
 */
@Component
public class ExcluirImagemImovelUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExcluirImagemImovelUseCase.class);

    private final ImovelRepository imovelRepository;
    private final ImovelFotoRepository fotoRepository;
    private final StoragePort storage;
    private final TransactionTemplate transaction;

    public ExcluirImagemImovelUseCase(ImovelRepository imovelRepository, ImovelFotoRepository fotoRepository,
                                      StoragePort storage, TransactionTemplate transaction) {
        this.imovelRepository = imovelRepository;
        this.fotoRepository = fotoRepository;
        this.storage = storage;
        this.transaction = transaction;
    }

    public void execute(UUID imovelId, UUID fotoId) {
        String objectPath = transaction.execute(status -> retirarDoImovel(imovelId, fotoId));
        if (objectPath == null) {
            return;
        }
        try {
            storage.delete(objectPath);
        } catch (StorageException ex) {
            log.error("Exclusão pendente: foto {} (objeto {}) não foi removida do Storage", fotoId, objectPath);
            throw ex;
        }
        transaction.executeWithoutResult(status -> fotoRepository.deleteById(fotoId));
    }

    /** Devolve o caminho do objeto a remover do Storage, ou null quando não há arquivo (foto por URL). */
    private String retirarDoImovel(UUID imovelId, UUID fotoId) {
        imovelRepository.findByIdForUpdate(imovelId).orElseThrow(() -> new ImovelNotFoundException(imovelId));
        ImovelFoto foto = fotoRepository.findByIdAndImovelId(fotoId, imovelId)
                .orElseThrow(() -> new ImovelFotoNotFoundException(fotoId));

        boolean eraPrincipal = foto.isPrincipal();
        String objectPath = null;
        if (foto.getStorageProvider() == StorageProvider.SUPABASE) {
            foto.setPrincipal(false);
            if (foto.getExclusaoPendenteEm() == null) {
                foto.setExclusaoPendenteEm(Instant.now().truncatedTo(ChronoUnit.MICROS));
            }
            objectPath = foto.getStoragePath();
        } else {
            fotoRepository.delete(foto);
        }

        if (eraPrincipal) {
            // Grava a saída da principal antes de promover outra: o banco aceita só uma por imóvel.
            fotoRepository.flush();
            fotoRepository.findFirstByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId)
                    .ifPresent(proxima -> proxima.setPrincipal(true));
        }
        return objectPath;
    }
}
