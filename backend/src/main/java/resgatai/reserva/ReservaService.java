package resgatai.reserva;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.lote.Lote;
import resgatai.lote.LoteRepository;
import resgatai.usuario.Instituicao;
import resgatai.usuario.UsuarioService;

import java.time.Instant;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservas;
    private final LoteRepository lotes;
    private final UsuarioService usuarioService;

    /**
     * UC04: o lock pessimista na linha do lote faz as requisições simultâneas esperarem a primeira terminar;
     * as seguintes já encontram o lote reservado e recebem 409 (fluxo 6a). O banco nunca grava reserva dupla.
     * ponytail: lock por linha no Postgres; fila/Redis só se a disputa por um mesmo lote virar gargalo.
     */
    @Transactional
    public ReservaResponse reservar(Long loteId, Long instituicaoId) {
        var instituicao = (Instituicao) usuarioService.buscar(instituicaoId); // SecurityConfig garante o perfil
        if (instituicao.getStatusAnalise() != Instituicao.StatusAnalise.APROVADA) { // RF06
            throw new ResponseStatusException(FORBIDDEN, "Cadastro em análise: aguarde a aprovação do administrador para reservar");
        }

        Lote lote = lotes.findComBloqueioById(loteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lote não encontrado"));
        if (lote.getStatus() != Lote.Status.DISPONIVEL || !lote.getEstabelecimento().isAtivo()) {
            throw new ResponseStatusException(CONFLICT, "O lote foi reservado por outra instituição e não está mais disponível");
        }
        if (!lote.getRetiradaFim().isAfter(Instant.now())) { // fluxo 6b; o B6 marca o lote como expirado
            throw new ResponseStatusException(CONFLICT, "O lote venceu ou está fora da janela de retirada");
        }

        lote.setStatus(Lote.Status.RESERVADO_PENDENTE);
        Reserva r = reservas.save(new Reserva(lote, instituicao));
        // TODO B7: notificar o doador sobre a reserva (UC04 passo 11)
        return ReservaResponse.from(r, true);
    }

    /** Instituição vê as próprias reservas, com o PIN; estabelecimento vê as reservas dos seus lotes, sem o PIN. */
    public Page<ReservaResponse> listar(Long usuarioId, Pageable pageable) {
        return usuarioService.buscar(usuarioId) instanceof Instituicao
                ? reservas.findByInstituicaoId(usuarioId, pageable).map(r -> ReservaResponse.from(r, true))
                : reservas.findByLoteEstabelecimentoId(usuarioId, pageable).map(r -> ReservaResponse.from(r, false));
    }
}
