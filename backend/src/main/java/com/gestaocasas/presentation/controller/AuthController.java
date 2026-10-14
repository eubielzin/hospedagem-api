package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.AlterarSenhaRequest;
import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.LoginRequest;
import com.gestaocasas.application.dto.LoginResponse;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.usecase.usuario.AlterarSenhaUseCase;
import com.gestaocasas.application.usecase.usuario.GetUsuarioAtualUseCase;
import com.gestaocasas.application.usecase.usuario.LoginUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final LoginUseCase login;
    private final GetUsuarioAtualUseCase getUsuarioAtual;
    private final AlterarSenhaUseCase alterarSenha;

    public AuthController(LoginUseCase login, GetUsuarioAtualUseCase getUsuarioAtual,
                          AlterarSenhaUseCase alterarSenha) {
        this.login = login;
        this.getUsuarioAtual = getUsuarioAtual;
        this.alterarSenha = alterarSenha;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica com e-mail e senha e devolve o token de acesso",
            description = "Responde 401 se o e-mail não existir, a senha estiver errada ou o usuário estiver "
                    + "inativo, sempre com a mesma mensagem.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login realizado com sucesso", login.execute(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário dono do token")
    public ResponseEntity<ApiResponse<UsuarioResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok("Usuário autenticado", getUsuarioAtual.execute(usuarioId(jwt))));
    }

    @PatchMapping("/senha")
    @Operation(summary = "Altera a senha do usuário dono do token",
            description = "Responde 422 se a senha atual estiver incorreta. O token em uso continua válido.")
    public ResponseEntity<ApiResponse<Void>> alterarSenha(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody AlterarSenhaRequest request) {
        alterarSenha.execute(usuarioId(jwt), request);
        return ResponseEntity.ok(ApiResponse.ok("Senha alterada com sucesso", null));
    }

    private static UUID usuarioId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
