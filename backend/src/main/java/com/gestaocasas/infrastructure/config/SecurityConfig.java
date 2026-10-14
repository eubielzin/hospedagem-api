package com.gestaocasas.infrastructure.config;

import com.gestaocasas.infrastructure.security.JwtConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Autenticação por token JWT emitido em POST /auth/login, enviado no cabeçalho
 * {@code Authorization: Bearer <token>}. A regra é fechada por padrão: só ficam abertos o login, o
 * health check, a documentação e a leitura pública de imóveis e fotos. Todo o resto, inclusive a
 * escrita de imóveis e fotos, exige token válido, em qualquer perfil.
 * <p>
 * Sem token, ou com token inválido ou expirado, a resposta é 401.
 */
@Configuration
@EnableWebSecurity
@Import(JwtConfig.class)
public class SecurityConfig {

    private static final String AUTENTICACAO_NECESSARIA = """
            {"success":false,"message":"Autenticação necessária"}""";
    private static final String TOKEN_INVALIDO = """
            {"success":false,"message":"Token inválido ou expirado"}""";
    private static final String ACESSO_NEGADO = """
            {"success":false,"message":"Acesso negado"}""";

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = SecurityConfig::recusarSemAutenticacao;
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Antes da leitura pública: o estoque fica sob /imoveis, mas é administrativo.
                        .requestMatchers("/imoveis/*/estoque", "/imoveis/*/estoque/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/imoveis", "/imoveis/*", "/imoveis/*/fotos").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, ex) ->
                                responder(response, HttpServletResponse.SC_FORBIDDEN, ACESSO_NEGADO)))
                .build();
    }

    private static void recusarSemAutenticacao(HttpServletRequest request,
                                               HttpServletResponse response,
                                               AuthenticationException ex) throws IOException {
        boolean tokenRecusado = ex instanceof OAuth2AuthenticationException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                tokenRecusado ? "Bearer error=\"invalid_token\"" : "Bearer");
        responder(response, HttpServletResponse.SC_UNAUTHORIZED,
                tokenRecusado ? TOKEN_INVALIDO : AUTENTICACAO_NECESSARIA);
    }

    private static void responder(HttpServletResponse response, int status, String corpo) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(corpo);
    }

    // Os usuários ficam na tabela usuario: evita que o Spring Boot gere um usuário padrão com senha no log.
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
