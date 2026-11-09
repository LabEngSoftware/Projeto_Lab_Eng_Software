package resgatai.lote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

/** RF02/UC02 cadastro e RF03 busca de lotes. Cadastro e foto restritos ao perfil ESTABELECIMENTO (SecurityConfig). */
@RestController
@RequestMapping("/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteService service;

    public record CriarLoteRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotNull Lote.Categoria categoria,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal quantidade,
            @NotNull Lote.Unidade unidade,
            @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal preco,
            @NotNull @Future(message = "lotes vencidos não podem ser publicados") Instant validade,
            @NotNull Instant retiradaInicio,
            @NotNull @Future Instant retiradaFim,
            @Size(max = 500) String observacoes,
            @Size(max = 255) String enderecoRetirada) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteResponse criar(@Valid @RequestBody CriarLoteRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(req, usuario(jwt));
    }

    /** Mais urgentes primeiro por padrão. */
    @GetMapping
    public PagedModel<LoteResponse> buscar(@Valid LoteFiltro filtro, @PageableDefault(sort = "validade") Pageable pageable,
                                           @AuthenticationPrincipal Jwt jwt) {
        return new PagedModel<>(service.buscar(filtro, usuario(jwt), pageable));
    }

    @GetMapping("/meus")
    public PagedModel<LoteResponse> meus(@PageableDefault(sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
                                         @AuthenticationPrincipal Jwt jwt) {
        return new PagedModel<>(service.meus(usuario(jwt), pageable));
    }

    @GetMapping("/{id}")
    public LoteResponse consultar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.consultar(id, usuario(jwt));
    }

    @PutMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarFoto(@PathVariable Long id, @RequestParam MultipartFile arquivo,
                           @AuthenticationPrincipal Jwt jwt) throws IOException {
        service.salvarFoto(id, usuario(jwt), arquivo.getContentType(), arquivo.getBytes());
    }

    /** Pública para poder ser usada direto em <img src>. */
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        var foto = service.foto(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(foto.getTipo())).body(foto.getConteudo());
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
