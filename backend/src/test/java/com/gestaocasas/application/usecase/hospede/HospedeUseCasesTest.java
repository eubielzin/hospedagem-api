package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapperImpl;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.enums.TipoDocumento;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospedeUseCasesTest {

    @Mock
    private HospedeRepository repository;

    private final HospedeMapperImpl mapper = new HospedeMapperImpl();
    private CreateHospedeUseCase create;
    private UpdateHospedeUseCase update;

    @BeforeEach
    void setUp() {
        HospedeDocumentoValidator documento = new HospedeDocumentoValidator(repository);
        create = new CreateHospedeUseCase(repository, mapper, documento);
        update = new UpdateHospedeUseCase(repository, mapper, documento);
        lenient().when(repository.saveAndFlush(any(Hospede.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static HospedeRequest request(TipoDocumento tipo, String numero) {
        return new HospedeRequest("  Maria da Silva ", tipo, numero, " Brasileira ", " ");
    }

    private static Hospede existente(UUID id, String nome) {
        Hospede hospede = new Hospede();
        hospede.setId(id);
        hospede.setNome(nome);
        return hospede;
    }

    @Test
    void cadastroNormalizaTextosEGuardaCpfSoComDigitos() {
        HospedeResponse response = create.execute(request(TipoDocumento.CPF, "529.982.247-25"));

        assertThat(response.nome()).isEqualTo("Maria da Silva");
        assertThat(response.numeroDocumento()).isEqualTo("52998224725");
        assertThat(response.nacionalidade()).isEqualTo("Brasileira");
        assertThat(response.telefone()).isNull();
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void passaporteFicaEmMaiusculasEDocumentoAusenteViraTextoVazio() {
        assertThat(create.execute(request(TipoDocumento.PASSAPORTE, " ab123456 ")).numeroDocumento())
                .isEqualTo("AB123456");
        assertThat(create.execute(request(TipoDocumento.CPF, null)).numeroDocumento()).isEmpty();
        // Sem documento não há o que comparar.
        verify(repository, never()).findByDocumento(eq(TipoDocumento.CPF), anyString());
    }

    @Test
    void documentoDeOutroHospedeResponde409ComONome() {
        when(repository.findByDocumento(TipoDocumento.CPF, "52998224725"))
                .thenReturn(Optional.of(existente(UUID.randomUUID(), "João Souza")));

        assertThatThrownBy(() -> create.execute(request(TipoDocumento.CPF, "529.982.247-25")))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um hóspede com este documento (João Souza).");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void edicaoAceitaOProprioDocumentoEMantemASituacao() {
        UUID id = UUID.randomUUID();
        Hospede atual = existente(id, "Maria");
        atual.setAtivo(false);
        when(repository.findById(id)).thenReturn(Optional.of(atual));
        when(repository.findByDocumento(TipoDocumento.CPF, "52998224725")).thenReturn(Optional.of(atual));

        HospedeResponse response = update.execute(id, request(TipoDocumento.CPF, "52998224725"));

        assertThat(response.nome()).isEqualTo("Maria da Silva");
        assertThat(response.ativo()).isFalse();
    }

    @Test
    void edicaoDeHospedeInexistenteResponde404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> update.execute(id, request(TipoDocumento.RG, "123")))
                .isInstanceOf(HospedeNotFoundException.class);
    }

    @Test
    void buscaEscapaCuringasESeparaOsDigitos() {
        new ListHospedesUseCase(repository, mapper).execute(" 529.982_% ", false);

        verify(repository).buscar("529.982!_!%", "529982", false);
    }

    @Test
    void historicoDeHospedeInexistenteResponde404() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> new HistoricoHospedeUseCase(repository, null, null).execute(id))
                .isInstanceOf(HospedeNotFoundException.class);
    }
}
