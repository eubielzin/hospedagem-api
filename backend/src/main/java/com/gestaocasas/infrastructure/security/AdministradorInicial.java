package com.gestaocasas.infrastructure.security;

import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.usecase.usuario.CreateUsuarioUseCase;
import com.gestaocasas.domain.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sem nenhum usuário cadastrado não há como fazer login, nem como cadastrar o primeiro usuário pela
 * API. Na subida, se a tabela estiver vazia e ADMIN_EMAIL e ADMIN_SENHA estiverem definidas, este
 * componente cria o primeiro usuário. Com algum usuário já cadastrado, as variáveis são ignoradas:
 * trocar ADMIN_SENHA depois não altera a senha.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioRepository repository;
    private final CreateUsuarioUseCase createUsuario;
    private final Validator validator;
    private final String nome;
    private final String email;
    private final String senha;

    public AdministradorInicial(UsuarioRepository repository, CreateUsuarioUseCase createUsuario, Validator validator,
                                @Value("${app.auth.admin-inicial.nome:Administrador}") String nome,
                                @Value("${app.auth.admin-inicial.email:}") String email,
                                @Value("${app.auth.admin-inicial.senha:}") String senha) {
        this.repository = repository;
        this.createUsuario = createUsuario;
        this.validator = validator;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhum usuário cadastrado: defina ADMIN_EMAIL e ADMIN_SENHA para criar o primeiro acesso");
            return;
        }
        UsuarioRequest request = new UsuarioRequest(nome, email, senha);
        Set<ConstraintViolation<UsuarioRequest>> violacoes = validator.validate(request);
        if (!violacoes.isEmpty()) {
            throw new IllegalStateException("Administrador inicial inválido: " + violacoes.stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
        }
        createUsuario.execute(request);
        log.info("Administrador inicial criado: {}", request.email().trim().toLowerCase(Locale.ROOT));
    }
}
