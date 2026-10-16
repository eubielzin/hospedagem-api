package com.gestaocasas.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endereço obrigatório, salvo no imóvel completo sem logradouro (ele recebe o da pousada), e a
 * capacidade informada por adultos ou, na forma antiga, pelo total.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImovelValidoValidator.class)
public @interface ImovelValido {

    String message() default "Imóvel inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
