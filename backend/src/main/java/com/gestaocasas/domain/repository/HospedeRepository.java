package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.enums.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospedeRepository extends JpaRepository<Hospede, UUID> {

    /**
     * Busca por parte do nome, sem diferenciar maiúsculas nem acentos, ou por parte dos dígitos do
     * documento, ignorando pontuação. Termo vazio lista todos. Ordem alfabética.
     *
     * @param termo   termo já com os curingas de LIKE escapados com "!"; vazio para não filtrar
     * @param digitos somente os dígitos do termo; vazio para não buscar por documento
     */
    @Query(value = """
            SELECT h.* FROM hospede h
            WHERE (h.ativo OR CAST(:incluirInativos AS boolean))
              AND (CAST(:termo AS text) = ''
                   OR h.nome_busca LIKE '%' || normalizar_busca(CAST(:termo AS text)) || '%' ESCAPE '!'
                   OR (CAST(:digitos AS text) <> ''
                       AND h.documento_busca LIKE '%' || CAST(:digitos AS text) || '%'))
            ORDER BY h.nome_busca, h.id
            """, nativeQuery = true)
    List<Hospede> buscar(@Param("termo") String termo, @Param("digitos") String digitos,
                         @Param("incluirInativos") boolean incluirInativos);

    /** Mesmo critério do índice único uk_hospede_documento: só letras e dígitos, em maiúsculas. */
    @Query(value = """
            SELECT h.* FROM hospede h
            WHERE h.tipo_documento = CAST(:tipo AS text)
              AND h.documento_busca = upper(regexp_replace(CAST(:numero AS text), '[^A-Za-z0-9]', '', 'g'))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Hospede> findByDocumento(@Param("tipo") String tipo, @Param("numero") String numero);

    default Optional<Hospede> findByDocumento(TipoDocumento tipo, String numero) {
        return findByDocumento(tipo.name(), numero);
    }
}
