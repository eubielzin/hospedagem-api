package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.AlterarSenhaRequest;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlterarSenhaUseCaseTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UsuarioRepository repository;

    private AlterarSenhaUseCase useCase;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        useCase = new AlterarSenhaUseCase(repository, passwordEncoder);
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setSenhaHash(passwordEncoder.encode("segredo123"));
    }

    @Test
    void trocaOHashQuandoASenhaAtualConfere() {
        when(repository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        useCase.execute(usuario.getId(), new AlterarSenhaRequest("segredo123", "novaSenha456"));

        assertThat(passwordEncoder.matches("novaSenha456", usuario.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("segredo123", usuario.getSenhaHash())).isFalse();
    }

    @Test
    void senhaAtualErradaNaoAlteraNada() {
        when(repository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        String hashAnterior = usuario.getSenhaHash();

        assertThatThrownBy(() -> useCase.execute(usuario.getId(), new AlterarSenhaRequest("errada", "novaSenha456")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Senha atual incorreta");
        assertThat(usuario.getSenhaHash()).isEqualTo(hashAnterior);
    }

    @Test
    void usuarioRemovidoResponde404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new AlterarSenhaRequest("a", "novaSenha456")))
                .isInstanceOf(UsuarioNotFoundException.class);
    }
}
