package com.gestaocasas.application.validation;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.domain.enums.TipoUh;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImovelValidoValidator implements ConstraintValidator<ImovelValido, ImovelRequest> {

    @Override
    public boolean isValid(ImovelRequest request, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        boolean valido = true;
        boolean enderecoDaPousada = request.tipo() == TipoUh.IMOVEL_COMPLETO && vazio(request.logradouro());
        if (!enderecoDaPousada) {
            valido &= exigir(!vazio(request.logradouro()), "logradouro", "Logradouro é obrigatório", context);
            valido &= exigir(!vazio(request.cidade()), "cidade", "Cidade é obrigatória", context);
            valido &= exigir(request.uf() != null, "uf", "UF é obrigatória", context);
        }
        valido &= exigir(request.capacidadeAdultos() != null || request.capacidadeMaxima() != null,
                "capacidadeAdultos", "Informe a capacidade de adultos", context);
        return valido;
    }

    private static boolean exigir(boolean condicao, String campo, String mensagem, ConstraintValidatorContext context) {
        if (!condicao) {
            context.buildConstraintViolationWithTemplate(mensagem).addPropertyNode(campo).addConstraintViolation();
        }
        return condicao;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
