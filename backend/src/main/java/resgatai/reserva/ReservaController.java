package resgatai.reserva;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** UC04 Reservar Lote e UC06 Validar Retirada. Reservar é da INSTITUICAO; validar, do ESTABELECIMENTO (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService service;

    @PostMapping("/lotes/{loteId}/reserva")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse reservar(@PathVariable Long loteId, @AuthenticationPrincipal Jwt jwt) {
        return service.reservar(loteId, Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/reservas")
    public PagedModel<ReservaResponse> listar(@PageableDefault(sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
                                              @AuthenticationPrincipal Jwt jwt) {
        return new PagedModel<>(service.listar(Long.valueOf(jwt.getSubject()), pageable));
    }

    /** codigo: conteúdo lido do QR Code ou o PIN digitado pelo doador. */
    public record CodigoRequest(@NotBlank String codigo) {}

    public record ValidarRetiradaRequest(@NotBlank String codigo,
                                         @Positive @Digits(integer = 8, fraction = 2) BigDecimal quantidadeEntregue,
                                         Boolean autorizarAtraso) {}

    public record CancelarRetiradaRequest(@NotBlank String codigo, @NotBlank @Size(max = 500) String motivo) {}

    @PostMapping("/retiradas/conferencia")
    public ReservaResponse conferir(@Valid @RequestBody CodigoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.conferir(req.codigo(), Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/retiradas")
    public ReservaResponse validar(@Valid @RequestBody ValidarRetiradaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.validar(req.codigo(), req.quantidadeEntregue(), Boolean.TRUE.equals(req.autorizarAtraso()), Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/retiradas/cancelamento")
    public ReservaResponse cancelar(@Valid @RequestBody CancelarRetiradaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.cancelar(req.codigo(), req.motivo(), Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/impacto")
    public ReservaService.ImpactoResponse impacto(@AuthenticationPrincipal Jwt jwt) {
        return service.impacto(Long.valueOf(jwt.getSubject()));
    }
}
