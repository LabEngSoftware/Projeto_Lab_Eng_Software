package resgatai.reserva;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.auditoria.Auditoria;
import resgatai.auditoria.AuditoriaRepository;
import resgatai.lote.Lote;
import resgatai.lote.LoteRepository;
import resgatai.usuario.Instituicao;
import resgatai.usuario.UsuarioService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservas;
    private final LoteRepository lotes;
    private final UsuarioService usuarioService;
    private final AuditoriaRepository auditoria;

    /** Pontos de reputação ganhos por retirada concluída; a instituição não pontua quando chega fora do prazo. */
    static final int PONTOS_RETIRADA = 5;
    private static final Pattern QR_CODE = Pattern.compile("RESGATAI-(\\d+)-(\\d{4})");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

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
        if (lote.getStatus() == Lote.Status.EXPIRADO || !lote.getRetiradaFim().isAfter(Instant.now())) { // fluxo 6b; o worker de Expiracao marca o lote
            throw new ResponseStatusException(CONFLICT, "O lote venceu ou está fora da janela de retirada");
        }
        if (lote.getStatus() != Lote.Status.DISPONIVEL || !lote.getEstabelecimento().isAtivo()) {
            throw new ResponseStatusException(CONFLICT, "O lote foi reservado por outra instituição e não está mais disponível");
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

    /** UC06 passos 5-7: identifica a reserva pelo código para o doador conferir lote e instituição antes de entregar. */
    @Transactional
    public ReservaResponse conferir(String codigo, Long doadorId) {
        return ReservaResponse.from(pendente(codigo, doadorId), false);
    }

    /**
     * UC06 passos 9-12: registra a entrega, invalida o código e atualiza reputação e impacto.
     * quantidadeEntregue menor que a do lote registra a divergência (4a); fora do prazo exige autorizarAtraso (6a).
     */
    @Transactional
    public ReservaResponse validar(String codigo, BigDecimal quantidadeEntregue, boolean autorizarAtraso, Long doadorId) {
        Reserva r = pendente(codigo, doadorId);
        boolean atrasada = Instant.now().isAfter(r.getPrazoRetirada());
        if (atrasada && !autorizarAtraso) {
            throw new ResponseStatusException(CONFLICT, "Prazo de retirada expirado em %s: autorize a entrega fora do prazo ou recuse a retirada"
                    .formatted(DATA_HORA.format(r.getPrazoRetirada())));
        }
        BigDecimal total = r.getLote().getQuantidade();
        BigDecimal entregue = quantidadeEntregue == null ? total : quantidadeEntregue;
        if (entregue.compareTo(total) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "A quantidade entregue não pode superar a do lote");
        }

        r.coletar(entregue, atrasada);
        var doador = r.getLote().getEstabelecimento();
        doador.setScore(doador.getScore() + PONTOS_RETIRADA);
        r.getInstituicao().setScore(r.getInstituicao().getScore() + (atrasada ? 0 : PONTOS_RETIRADA));
        auditoria.save(new Auditoria(doadorId, Auditoria.Acao.RETIRADA_VALIDADA, "RESERVA", r.getId(),
                "%s %s de %s%s".formatted(entregue.toPlainString(), r.getLote().getUnidade(), total.toPlainString(), atrasada ? ", fora do prazo" : "")));
        // TODO B7: notificar a instituição da conclusão (UC06 passo 12)
        return ReservaResponse.from(r, false);
    }

    /** UC06 7a: o doador recusa a entrega por divergência; lote e reserva ficam cancelados. */
    @Transactional
    public ReservaResponse cancelar(String codigo, String motivo, Long doadorId) {
        Reserva r = pendente(codigo, doadorId);
        r.cancelar(motivo);
        auditoria.save(new Auditoria(doadorId, Auditoria.Acao.RETIRADA_CANCELADA, "RESERVA", r.getId(), motivo));
        // TODO B7: notificar a instituição do cancelamento
        return ReservaResponse.from(r, false);
    }

    /** Impacto da plataforma inteira e do usuário logado, como doador ou como receptor. */
    public ImpactoResponse impacto(Long usuarioId) {
        return new ImpactoResponse(Impacto.de(reservas.impacto(null)), Impacto.de(reservas.impacto(usuarioId)));
    }

    public record ImpactoResponse(Impacto plataforma, Impacto meu) {}

    /**
     * Localiza a reserva pelo conteúdo do QR Code ou pelo PIN digitado (4a), só entre as dos lotes do doador,
     * travando-a para que dois cliques (ou o worker de Expiracao) não a processem duas vezes.
     */
    private Reserva pendente(String codigo, Long doadorId) {
        Matcher qr = QR_CODE.matcher(codigo.strip());
        List<Reserva> candidatas = qr.matches()
                ? reservas.findComBloqueioById(Long.valueOf(qr.group(1))).filter(r -> r.getPin().equals(qr.group(2))).stream().toList()
                : reservas.findComBloqueioByLoteEstabelecimentoIdAndPinOrderByCriadoEmDesc(doadorId, codigo.strip());
        candidatas = candidatas.stream().filter(r -> r.getLote().getEstabelecimento().getId().equals(doadorId)).toList();
        var pendentes = candidatas.stream().filter(r -> r.getStatus() == Reserva.Status.PENDENTE).toList();
        // ponytail: PIN de 4 dígitos pode repetir entre reservas pendentes do mesmo doador; o QR Code desambigua
        if (pendentes.size() > 1) {
            throw new ResponseStatusException(CONFLICT, "Mais de uma reserva pendente com este PIN: leia o QR Code");
        }
        Reserva r = (pendentes.isEmpty() ? candidatas.stream() : pendentes.stream()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Código inválido ou inexistente")); // 5a

        switch (r.getStatus()) {
            case PENDENTE -> { }
            case COLETADA -> throw new ResponseStatusException(CONFLICT, "Retirada já registrada em %s por %s; não pode ser validada novamente"
                    .formatted(DATA_HORA.format(r.getValidadoEm()), r.getLote().getEstabelecimento().getNome())); // 5b
            case CANCELADA -> throw new ResponseStatusException(CONFLICT, "Retirada cancelada em %s: %s"
                    .formatted(DATA_HORA.format(r.getValidadoEm()), r.getMotivoCancelamento()));
            case EXPIRADA -> throw new ResponseStatusException(CONFLICT, "Reserva expirada: o prazo de retirada acabou em %s e o lote voltou às buscas"
                    .formatted(DATA_HORA.format(r.getPrazoRetirada())));
        }
        return r;
    }
}
