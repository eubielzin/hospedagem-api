package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.application.validation.ImagemValidada;
import com.gestaocasas.application.validation.ImagemValidator;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StorageProvider;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.storage.StoragePort;
import com.gestaocasas.domain.storage.StorageUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * O upload HTTP não participa da transação do banco: o método não é transacional. Só a gravação
 * da referência é, e se ela falhar o objeto recém-enviado é removido do Storage (compensação).
 */
@Component
public class UploadImagemImovelUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadImagemImovelUseCase.class);

    private final ImovelRepository imovelRepository;
    private final ImovelFotoRepository fotoRepository;
    private final ImagemValidator validator;
    private final StoragePort storage;
    private final ImovelMapper mapper;
    private final TransactionTemplate transaction;

    public UploadImagemImovelUseCase(ImovelRepository imovelRepository, ImovelFotoRepository fotoRepository,
                                     ImagemValidator validator, StoragePort storage, ImovelMapper mapper,
                                     TransactionTemplate transaction) {
        this.imovelRepository = imovelRepository;
        this.fotoRepository = fotoRepository;
        this.validator = validator;
        this.storage = storage;
        this.mapper = mapper;
        this.transaction = transaction;
    }

    public ImovelFotoResponse execute(UUID imovelId, byte[] conteudo, String contentType, String descricao,
                                      boolean principal) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        ImagemValidada imagem = validator.validar(conteudo, contentType);

        // O caminho vem só de identificadores gerados aqui; o nome original do arquivo não é usado.
        String objectPath = imovelId + "/" + UUID.randomUUID() + "." + imagem.formato().extensao();
        StorageUploadResult enviado = storage.upload(objectPath, conteudo, imagem.formato().mimeType());

        try {
            return transaction.execute(status -> registrar(imovelId, enviado, descricao, principal));
        } catch (RuntimeException ex) {
            compensar(objectPath);
            throw ex;
        }
    }

    private ImovelFotoResponse registrar(UUID imovelId, StorageUploadResult enviado, String descricao,
                                         boolean principal) {
        Imovel imovel = imovelRepository.findByIdForUpdate(imovelId)
                .orElseThrow(() -> new ImovelNotFoundException(imovelId));

        // A primeira foto do imóvel vira a principal mesmo sem pedido explícito.
        boolean tornarPrincipal = principal || !fotoRepository.existsByImovelIdAndPrincipalTrue(imovelId);
        if (tornarPrincipal) {
            fotoRepository.desmarcarPrincipal(imovelId);
        }

        ImovelFoto foto = new ImovelFoto();
        foto.setImovel(imovel);
        foto.setUrl(enviado.publicUrl());
        foto.setDescricao(descricao == null || descricao.isBlank() ? null : descricao.strip());
        foto.setOrdem(fotoRepository.findMaiorOrdem(imovelId) + 1);
        foto.setStorageProvider(StorageProvider.SUPABASE);
        foto.setStoragePath(enviado.objectPath());
        foto.setPrincipal(tornarPrincipal);
        return mapper.toResponse(fotoRepository.saveAndFlush(foto));
    }

    private void compensar(String objectPath) {
        try {
            storage.delete(objectPath);
            log.warn("Gravação da foto falhou; objeto {} removido do Storage", objectPath);
        } catch (RuntimeException ex) {
            // Sem registro no banco, este log é a única pista para a limpeza manual do bucket.
            log.error("OBJETO ORFAO NO STORAGE: {} foi enviado, a gravação no banco falhou e a remoção também ({})",
                    objectPath, ex.getMessage());
        }
    }
}
