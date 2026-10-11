package com.gestaocasas.domain.storage;

import com.gestaocasas.domain.exception.StorageException;

/**
 * Armazenamento de arquivos. O caminho do objeto é sempre gerado pela aplicação, nunca pelo cliente.
 */
public interface StoragePort {

    /** Envia um objeto novo; não sobrescreve um caminho existente. */
    StorageUploadResult upload(String objectPath, byte[] content, String contentType) throws StorageException;

    /** Remove o objeto. Um objeto que já não existe é tratado como removido. */
    void delete(String objectPath) throws StorageException;

    String getPublicUrl(String objectPath);
}
