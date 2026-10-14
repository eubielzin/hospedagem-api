package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ApiResponse;
import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.usecase.imovel.AddImovelFotoUseCase;
import com.gestaocasas.application.usecase.imovel.DefinirFotoPrincipalUseCase;
import com.gestaocasas.application.usecase.imovel.ExcluirImagemImovelUseCase;
import com.gestaocasas.application.usecase.imovel.ListarFotosImovelUseCase;
import com.gestaocasas.application.usecase.imovel.UploadImagemImovelUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/imoveis/{imovelId}/fotos")
@Tag(name = "Fotos do imóvel")
public class ImovelFotoController {

    private final AddImovelFotoUseCase addImovelFoto;
    private final UploadImagemImovelUseCase uploadImagem;
    private final ListarFotosImovelUseCase listarFotos;
    private final ExcluirImagemImovelUseCase excluirImagem;
    private final DefinirFotoPrincipalUseCase definirFotoPrincipal;

    public ImovelFotoController(AddImovelFotoUseCase addImovelFoto, UploadImagemImovelUseCase uploadImagem,
                                ListarFotosImovelUseCase listarFotos, ExcluirImagemImovelUseCase excluirImagem,
                                DefinirFotoPrincipalUseCase definirFotoPrincipal) {
        this.addImovelFoto = addImovelFoto;
        this.uploadImagem = uploadImagem;
        this.listarFotos = listarFotos;
        this.excluirImagem = excluirImagem;
        this.definirFotoPrincipal = definirFotoPrincipal;
    }

    @GetMapping
    @Operation(summary = "Lista as fotos do imóvel, na ordem de exibição")
    public ResponseEntity<ApiResponse<List<ImovelFotoResponse>>> list(@PathVariable UUID imovelId) {
        return ResponseEntity.ok(ApiResponse.ok("Fotos recuperadas com sucesso", listarFotos.execute(imovelId)));
    }

    @PostMapping
    @Operation(summary = "Cadastra uma foto a partir de uma URL externa")
    public ResponseEntity<ApiResponse<ImovelFotoResponse>> addFoto(@PathVariable UUID imovelId,
                                                                   @Valid @RequestBody ImovelFotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Foto cadastrada com sucesso", addImovelFoto.execute(imovelId, request)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia uma imagem (JPEG, PNG ou WebP, até 5 MB) para o Supabase Storage",
            description = "A primeira foto do imóvel vira a principal.")
    public ResponseEntity<ApiResponse<ImovelFotoResponse>> upload(
            @PathVariable UUID imovelId,
            @Parameter(description = "Arquivo da imagem") @RequestPart("file") MultipartFile file,
            @RequestParam(required = false)
            @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres") String descricao,
            @RequestParam(defaultValue = "false") boolean principal) throws IOException {
        ImovelFotoResponse foto = uploadImagem.execute(imovelId, file.getBytes(), file.getContentType(),
                descricao, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Imagem enviada com sucesso", foto));
    }

    @DeleteMapping("/{fotoId}")
    @Operation(summary = "Exclui uma foto; se for um arquivo enviado, remove-o também do Storage",
            description = "Se o Storage falhar a resposta é 502 e a exclusão fica pendente: repita a chamada.")
    public ResponseEntity<Void> removeFoto(@PathVariable UUID imovelId, @PathVariable UUID fotoId) {
        excluirImagem.execute(imovelId, fotoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{fotoId}/principal")
    @Operation(summary = "Define a foto principal do imóvel",
            description = "A principal anterior deixa de ser.")
    public ResponseEntity<ApiResponse<ImovelFotoResponse>> definirPrincipal(@PathVariable UUID imovelId,
                                                                            @PathVariable UUID fotoId) {
        return ResponseEntity.ok(ApiResponse.ok("Foto principal definida com sucesso",
                definirFotoPrincipal.execute(imovelId, fotoId)));
    }
}
