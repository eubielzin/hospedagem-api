package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.LoginRequest;
import com.gestaocasas.application.dto.LoginResponse;
import com.gestaocasas.application.mapper.UsuarioMapperImpl;
import com.gestaocasas.application.service.EmissorDeToken;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.CredenciaisInvalidasException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    // Custo mínimo do BCrypt: o teste não precisa da lentidão de produção.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UsuarioRepository repository;
    @Mock
    private EmissorDeToken emissorDeToken;

    private LoginUseCase useCase;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(repository, passwordEncoder, emissorDeToken, new UsuarioMapperImpl());
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Ana");
        usuario.setEmail("ana@exemplo.com");
        usuario.setSenhaHash(passwordEncoder.encode("segredo123"));
        usuario.setAtivo(true);
        lenient().when(emissorDeToken.emitir(any()))
                .thenReturn(new EmissorDeToken.TokenEmitido("token", Instant.parse("2026-10-14T20:00:00Z")));
    }

    @Test
    void credenciaisCorretasDevolvemToken() {
        when(repository.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.of(usuario));

        LoginResponse response = useCase.execute(new LoginRequest("  ana@exemplo.com ", "segredo123"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.tipo()).isEqualTo("Bearer");
        assertThat(response.expiraEm()).isEqualTo("2026-10-14T20:00:00Z");
        assertThat(response.usuario().id()).isEqualTo(usuario.getId());
    }

    @Test
    void senhaErradaERecusada() {
        when(repository.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> useCase.execute(new LoginRequest("ana@exemplo.com", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(emissorDeToken);
    }

    @Test
    void emailInexistenteERecusadoComAMesmaMensagem() {
        when(repository.findByEmailIgnoreCase("ninguem@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LoginRequest("ninguem@exemplo.com", "segredo123")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos");
        verifyNoInteractions(emissorDeToken);
    }

    @Test
    void usuarioInativoERecusadoMesmoComSenhaCorreta() {
        usuario.setAtivo(false);
        when(repository.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> useCase.execute(new LoginRequest("ana@exemplo.com", "segredo123")))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(emissorDeToken);
    }
}
