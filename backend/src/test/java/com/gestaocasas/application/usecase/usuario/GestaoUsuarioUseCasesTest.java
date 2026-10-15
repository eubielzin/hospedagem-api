package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.RedefinirSenhaRequest;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.dto.UsuarioUpdateRequest;
import com.gestaocasas.application.mapper.UsuarioMapperImpl;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestaoUsuarioUseCasesTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UsuarioMapperImpl mapper = new UsuarioMapperImpl();

    @Mock
    private UsuarioRepository repository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Bruno");
        usuario.setEmail("bruno@exemplo.com");
        usuario.setSenhaHash(passwordEncoder.encode("senhaAntiga1"));
        usuario.setAtivo(true);
        lenient().when(repository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        lenient().when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void atualizaNomeEEmailNormalizado() {
        UsuarioResponse response = new UpdateUsuarioUseCase(repository, mapper)
                .execute(usuario.getId(), new UsuarioUpdateRequest(" Bruno Lima ", " Bruno.Lima@Exemplo.com "));

        assertThat(response.nome()).isEqualTo("Bruno Lima");
        assertThat(response.email()).isEqualTo("bruno.lima@exemplo.com");
    }

    @Test
    void emailDeOutroUsuarioResponde409() {
        when(repository.existsByEmailIgnoreCaseAndIdNot("ana@exemplo.com", usuario.getId())).thenReturn(true);

        assertThatThrownBy(() -> new UpdateUsuarioUseCase(repository, mapper)
                .execute(usuario.getId(), new UsuarioUpdateRequest("Bruno", "ANA@exemplo.com")))
                .isInstanceOf(ConflitoException.class);
        assertThat(usuario.getEmail()).isEqualTo("bruno@exemplo.com");
    }

    @Test
    void inativaEReativaOutroUsuario() {
        AlterarSituacaoUsuarioUseCase useCase = new AlterarSituacaoUsuarioUseCase(repository, mapper);

        assertThat(useCase.inativar(usuario.getId(), UUID.randomUUID()).ativo()).isFalse();
        assertThat(useCase.reativar(usuario.getId()).ativo()).isTrue();
    }

    @Test
    void ninguemInativaASiMesmo() {
        assertThatThrownBy(() -> new AlterarSituacaoUsuarioUseCase(repository, mapper)
                .inativar(usuario.getId(), usuario.getId()))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(usuario.isAtivo()).isTrue();
    }

    @Test
    void redefineSenhaSemPedirAAtual() {
        new RedefinirSenhaUseCase(repository, passwordEncoder)
                .execute(usuario.getId(), new RedefinirSenhaRequest("senhaNova123"));

        assertThat(passwordEncoder.matches("senhaNova123", usuario.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("senhaAntiga1", usuario.getSenhaHash())).isFalse();
    }

    @Test
    void verificacaoDeSiMesmoAconteceAntesDeConsultarOBanco() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new AlterarSituacaoUsuarioUseCase(repository, mapper).inativar(id, id))
                .isInstanceOf(RegraDeNegocioException.class);
        verifyNoInteractions(repository);
    }
}
