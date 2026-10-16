package com.gestaocasas.application.usecase.uh;

import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.application.service.GovernancaUh;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListUhsUseCase {

    private final GovernancaUh governanca;

    public ListUhsUseCase(GovernancaUh governanca) {
        this.governanca = governanca;
    }

    @Transactional(readOnly = true)
    public List<UhResponse> execute() {
        return governanca.listar();
    }
}
