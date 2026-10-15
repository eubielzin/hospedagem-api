package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.RedefinirSenhaRequest;
import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.dto.UsuarioUpdateRequest;
import com.gestaocasas.application.usecase.usuario.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Usuários do painel. Todos têm o mesmo acesso: ainda não há perfis. "
        + "Usuários não são excluídos, só inativados.")
public class UsuarioController {

    private final CreateUsuarioUseCase createUsuario;
    private final ListUsuariosUseCase listUsuarios;
    private final GetUsuarioByIdUseCase getUsuarioById;
    private final UpdateUsuarioUseCase updateUsuario;
    private final AlterarSituacaoUsuarioUseCase alterarSituacao;
    private final RedefinirSenhaUseCase redefinirSenha;

    public UsuarioController(CreateUsuarioUseCase createUsuario, ListUsuariosUseCase listUsuarios,
                             GetUsuarioByIdUseCase getUsuarioById, UpdateUsuarioUseCase updateUsuario,
                             AlterarSituacaoUsuarioUseCase alterarSituacao, RedefinirSenhaUseCase redefinirSenha) {
        this.createUsuario = createUsuario;
        this.listUsuarios = listUsuarios;
        this.getUsuarioById = getUsuarioById;
        this.updateUsuario = updateUsuario;
        this.alterarSituacao = alterarSituacao;
        this.redefinirSenha = redefinirSenha;
    }

    @PostMapping
    @Operation(summary = "Cadastra um usuário", description = "Responde 409 se o e-mail já estiver em uso.")
    public ResponseEntity<ApiResponse<UsuarioResponse>> create(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuário criado com sucesso", createUsuario.execute(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Usuários recuperados com sucesso", listUsuarios.execute()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuário encontrado", getUsuarioById.execute(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome e e-mail", description = "Responde 409 se o e-mail já pertencer a outro "
            + "usuário. A senha muda em PATCH /usuarios/{id}/senha ou PATCH /auth/senha.")
    public ResponseEntity<ApiResponse<UsuarioResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Usuário atualizado com sucesso", updateUsuario.execute(id, request)));
    }

    @PatchMapping("/{id}/inativar")
    @Operation(summary = "Inativa um usuário",
            description = "O acesso é cortado na hora. Responde 422 se for o próprio usuário logado.")
    public ResponseEntity<ApiResponse<UsuarioResponse>> inativar(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok("Usuário inativado com sucesso",
                alterarSituacao.inativar(id, UUID.fromString(jwt.getSubject()))));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<ApiResponse<UsuarioResponse>> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuário reativado com sucesso", alterarSituacao.reativar(id)));
    }

    @PatchMapping("/{id}/senha")
    @Operation(summary = "Redefine a senha de um usuário", description = "Não pede a senha atual; para a própria "
            + "senha, prefira PATCH /auth/senha.")
    public ResponseEntity<ApiResponse<Void>> redefinirSenha(@PathVariable UUID id,
                                                            @Valid @RequestBody RedefinirSenhaRequest request) {
        redefinirSenha.execute(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Senha redefinida com sucesso", null));
    }
}
