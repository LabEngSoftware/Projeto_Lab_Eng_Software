package resgatai.notificacao;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Caixa de entrada do usuário logado; o PWA consulta periodicamente e exibe as não lidas. */
@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoRepository notificacoes;

    @GetMapping
    public PagedModel<Notificacao> listar(@PageableDefault(sort = {"criadoEm", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
                                          @AuthenticationPrincipal Jwt jwt) {
        return new PagedModel<>(notificacoes.findByUsuarioId(Long.valueOf(jwt.getSubject()), pageable));
    }

    @PutMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void marcarLida(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        notificacoes.findByIdAndUsuarioId(id, Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificação não encontrada"))
                .setLida(true);
    }
}
