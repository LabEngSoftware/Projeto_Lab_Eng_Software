package resgatai.reserva;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** UC04 Reservar Lote. Reservar é restrito ao perfil INSTITUICAO (SecurityConfig). */
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
}
