package resgatai.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.br.CNPJ;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** UC01 Gerenciar Usuários e RF06 Aprovação de instituições. Acesso restrito ao perfil ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioService service;

    public record CriarUsuarioRequest(
            @NotNull Usuario.Perfil perfil,
            @NotBlank @Size(max = 150) String nome,
            @CNPJ String documento,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 20) String telefone,
            @NotBlank @Size(max = 255) String endereco) {}

    public record AtualizarUsuarioRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 20) String telefone,
            @NotBlank @Size(max = 255) String endereco) {}

    public record RejeitarRequest(@NotBlank @Size(max = 500) String motivo) {}

    public record UsuarioCriadoResponse(UsuarioResponse usuario, String linkPrimeiroAcesso) {}

    @GetMapping
    public PagedModel<UsuarioResponse> listar(UsuarioFiltro filtro, Pageable pageable) {
        return new PagedModel<>(service.listar(filtro, pageable).map(UsuarioResponse::from));
    }

    @GetMapping("/{id}")
    public UsuarioResponse consultar(@PathVariable Long id) {
        return UsuarioResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioCriadoResponse criar(@Valid @RequestBody CriarUsuarioRequest req, @AuthenticationPrincipal Jwt jwt) {
        var criado = service.criar(req.perfil(), req.nome(), req.documento(), req.email(),
                req.telefone(), req.endereco(), autor(jwt));
        return new UsuarioCriadoResponse(UsuarioResponse.from(criado.usuario()), criado.linkPrimeiroAcesso());
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.from(service.atualizar(id, req.nome(), req.email(), req.telefone(), req.endereco(), autor(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.inativar(id, autor(jwt));
    }

    @PostMapping("/{id}/aprovar")
    public UsuarioResponse aprovar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.from(service.analisarInstituicao(id, Instituicao.StatusAnalise.APROVADA, null, autor(jwt)));
    }

    @PostMapping("/{id}/rejeitar")
    public UsuarioResponse rejeitar(@PathVariable Long id, @Valid @RequestBody RejeitarRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.from(service.analisarInstituicao(id, Instituicao.StatusAnalise.REJEITADA, req.motivo(), autor(jwt)));
    }

    private static Long autor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
