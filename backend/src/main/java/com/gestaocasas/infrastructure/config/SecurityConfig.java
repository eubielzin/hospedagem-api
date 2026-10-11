package com.gestaocasas.infrastructure.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Autenticação ainda não faz parte do sistema: as rotas estão liberadas, com uma exceção.
 * As operações de escrita em fotos usam a chave secreta do Supabase Storage e por isso ficam
 * bloqueadas, a menos que FOTOS_ESCRITA_HABILITADA=true (uso local). Quando o módulo de
 * autenticação entrar, essa chave dá lugar a regras de acesso por usuário, definidas aqui.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ESCRITA_DESABILITADA = """
            {"success":false,"message":"Operação desabilitada neste ambiente: a API ainda não possui autenticação"}""";

    private final CorsConfigurationSource corsConfigurationSource;
    private final boolean escritaDeFotosHabilitada;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                          @Value("${app.fotos.escrita-habilitada:false}") boolean escritaDeFotosHabilitada) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.escritaDeFotosHabilitada = escritaDeFotosHabilitada;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> {
                    if (!escritaDeFotosHabilitada) {
                        auth.requestMatchers(HttpMethod.POST, "/imoveis/*/fotos/upload").denyAll()
                                .requestMatchers(HttpMethod.DELETE, "/imoveis/*/fotos/*").denyAll()
                                .requestMatchers(HttpMethod.PATCH, "/imoveis/*/fotos/*/principal").denyAll();
                    }
                    auth.anyRequest().permitAll();
                })
                // Sem login, toda requisição é anônima: a recusa chega pelo entry point.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> recusar(response))
                        .accessDeniedHandler((request, response, ex) -> recusar(response)))
                .build();
    }

    private static void recusar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ESCRITA_DESABILITADA);
    }

    // Sem usuários: evita que o Spring Boot gere um usuário padrão com senha no log.
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
