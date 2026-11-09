package resgatai.auditoria;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaRepository auditoria;

    /** Histórico de operações; filtra por usuário alvo quando informado (UC01 fluxo 3a). */
    @GetMapping
    public PagedModel<Auditoria> listar(@RequestParam(required = false) Long usuarioId,
                                        @PageableDefault(sort = {"criadoEm", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        var pagina = usuarioId == null
                ? auditoria.findAll(pageable)
                : auditoria.findByEntidadeAndEntidadeId("USUARIO", usuarioId, pageable);
        return new PagedModel<>(pagina);
    }
}
