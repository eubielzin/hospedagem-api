package com.gestaocasas.application.validation;

import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.domain.enums.TipoDocumento;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentoValidoValidator implements ConstraintValidator<DocumentoValido, HospedeRequest> {

    @Override
    public boolean isValid(HospedeRequest request, ConstraintValidatorContext context) {
        if (request.tipoDocumento() != TipoDocumento.CPF || request.numeroDocumento() == null
                || request.numeroDocumento().isBlank()) {
            return true;
        }
        if (Cpf.valido(request.numeroDocumento().replaceAll("\\D", ""))) {
            return true;
        }
        // O erro aparece no campo do número, como o formulário espera.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("numeroDocumento")
                .addConstraintViolation();
        return false;
    }
}
