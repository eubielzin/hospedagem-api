package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.mapper.UsuarioMapperImpl;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUsuarioUseCaseTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UsuarioRepository repository;

    private CreateUsuarioUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUsuarioUseCase(repository, passwordEncoder, new UsuarioMapperImpl());
    }

    @Test
    void guardaEmailNormalizadoESomenteOHashDaSenha() {
        when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = useCase.execute(new UsuarioRequest(" Ana ", " Ana@Exemplo.COM ", "segredo123"));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).saveAndFlush(salvo.capture());
        assertThat(salvo.getValue().getNome()).isEqualTo("Ana");
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(salvo.getValue().getSenhaHash()).isNotEqualTo("segredo123");
        assertThat(passwordEncoder.matches("segredo123", salvo.getValue().getSenhaHash())).isTrue();
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void emailJaCadastradoResponde409() {
        when(repository.existsByEmailIgnoreCase("ana@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new UsuarioRequest("Ana", "ANA@exemplo.com", "segredo123")))
                .isInstanceOf(ConflitoException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
