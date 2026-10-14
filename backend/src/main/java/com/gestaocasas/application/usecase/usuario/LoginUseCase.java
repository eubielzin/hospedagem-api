package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.LoginRequest;
import com.gestaocasas.application.dto.LoginResponse;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.application.service.EmissorDeToken;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.CredenciaisInvalidasException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class LoginUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmissorDeToken emissorDeToken;
    private final UsuarioMapper mapper;
    // Comparado quando o e-mail não existe: a resposta leva o mesmo tempo e não revela o cadastro.
    private final String hashFicticio;

    public LoginUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder,
                        EmissorDeToken emissorDeToken, UsuarioMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emissorDeToken = emissorDeToken;
        this.mapper = mapper;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        Optional<Usuario> encontrado = repository.findByEmailIgnoreCase(request.email().trim());
        boolean senhaConfere = passwordEncoder.matches(request.senha(),
                encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio));
        Usuario usuario = encontrado
                .filter(u -> senhaConfere && u.isAtivo())
                .orElseThrow(CredenciaisInvalidasException::new);

        EmissorDeToken.TokenEmitido token = emissorDeToken.emitir(usuario);
        return new LoginResponse(token.token(), "Bearer", token.expiraEm(), mapper.toResponse(usuario));
    }
}
