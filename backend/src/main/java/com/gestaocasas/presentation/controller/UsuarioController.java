package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.usecase.usuario.CreateUsuarioUseCase;
import com.gestaocasas.application.usecase.usuario.ListUsuariosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Usuários do painel. Todos têm o mesmo acesso: ainda não há perfis.")
public class UsuarioController {

    private final CreateUsuarioUseCase createUsuario;
    private final ListUsuariosUseCase listUsuarios;

    public UsuarioController(CreateUsuarioUseCase createUsuario, ListUsuariosUseCase listUsuarios) {
        this.createUsuario = createUsuario;
        this.listUsuarios = listUsuarios;
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
}
