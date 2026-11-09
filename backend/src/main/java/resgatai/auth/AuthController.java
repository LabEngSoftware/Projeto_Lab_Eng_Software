package resgatai.auth;

import resgatai.usuario.Usuario;
import resgatai.usuario.UsuarioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.br.CNPJ;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    public record RegistroRequest(
            @NotNull Usuario.Perfil perfil,
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @CNPJ String documento,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotBlank @Size(max = 20) String telefone,
            @NotBlank @Size(max = 255) String endereco,
            @NotNull @AssertTrue(message = "é necessário aceitar os termos de uso e a política de privacidade (LGPD)")
            Boolean aceiteLgpd) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {}

    public record TokenResponse(String token) {}

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest req) {
        return UsuarioResponse.from(auth.registrar(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return new TokenResponse(auth.login(req.email(), req.senha()));
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.from(auth.usuarioAtual(jwt));
    }
}
