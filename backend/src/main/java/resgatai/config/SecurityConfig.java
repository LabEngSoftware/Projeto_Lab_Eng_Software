package resgatai.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import resgatai.usuario.Usuario;
import resgatai.usuario.UsuarioRepository;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    private final SecretKey chave;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret) {
        // HS256 exige chave de pelo menos 32 bytes
        this.chave = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // API stateless com token Bearer: sem sessão, sem cookie, então CSRF não se aplica
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/auth/registro", "/auth/login", "/auth/definir-senha").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/lotes/*/foto").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("SCOPE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/lotes").hasAuthority("SCOPE_ESTABELECIMENTO")
                        .requestMatchers(HttpMethod.PUT, "/lotes/*/foto").hasAuthority("SCOPE_ESTABELECIMENTO")
                        .requestMatchers("/lotes/meus").hasAuthority("SCOPE_ESTABELECIMENTO")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    /** Além de assinatura e expiração, rejeita tokens de usuários inativados (UC01 3c: revoga o acesso na hora). */
    @Bean
    JwtDecoder jwtDecoder(UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).build();
        OAuth2TokenValidator<Jwt> usuarioAtivo = jwt ->
                usuarios.findById(Long.valueOf(jwt.getSubject())).filter(Usuario::isAtivo).isPresent()
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Usuário inativo", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), usuarioAtivo));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
