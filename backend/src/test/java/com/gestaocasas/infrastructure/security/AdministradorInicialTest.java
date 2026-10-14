package com.gestaocasas.infrastructure.security;

import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.usecase.usuario.CreateUsuarioUseCase;
import com.gestaocasas.domain.repository.UsuarioRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UsuarioRepository repository;
    @Mock
    private CreateUsuarioUseCase createUsuario;

    private AdministradorInicial com(String email, String senha) {
        return new AdministradorInicial(repository, createUsuario, validator, "Admin", email, senha);
    }

    @Test
    void criaOPrimeiroUsuarioQuandoATabelaEstaVazia() {
        when(repository.count()).thenReturn(0L);

        com("admin@exemplo.com", "segredo123").run(null);

        verify(createUsuario).execute(new UsuarioRequest("Admin", "admin@exemplo.com", "segredo123"));
    }

    @Test
    void ignoraAsVariaveisQuandoJaExisteUsuario() {
        when(repository.count()).thenReturn(1L);

        com("admin@exemplo.com", "outraSenha999").run(null);

        verifyNoInteractions(createUsuario);
    }

    @Test
    void semVariaveisNaoCriaNada() {
        when(repository.count()).thenReturn(0L);

        com("", "").run(null);

        verifyNoInteractions(createUsuario);
    }

    @Test
    void senhaFracaImpedeASubida() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> com("admin@exemplo.com", "123").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Senha deve ter entre 8 e 72 caracteres");
        verifyNoInteractions(createUsuario);
    }
}
