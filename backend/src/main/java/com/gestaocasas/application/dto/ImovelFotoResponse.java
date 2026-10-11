package com.gestaocasas.application.dto;

import java.util.UUID;

public record ImovelFotoResponse(UUID id, String url, String descricao, Integer ordem, boolean principal) {
}
