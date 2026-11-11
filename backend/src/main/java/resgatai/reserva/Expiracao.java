package resgatai.reserva;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import resgatai.auditoria.Auditoria;
import resgatai.auditoria.AuditoriaRepository;
import resgatai.lote.Lote;
import resgatai.lote.LoteRepository;
import resgatai.notificacao.Notificador;
import resgatai.pagamento.GatewayPagamento;

import java.time.Duration;
import java.time.Instant;

/**
 * Worker de expiração (Gestão de Prazos e RNF02), rodando pelo cron app.expiracao.cron:
 * reservas não retiradas no prazo voltam o lote às buscas e penalizam a instituição;
 * lotes disponíveis cuja janela de retirada acabou passam a "Expirado". Na auditoria, autor nulo = sistema.
 * ponytail: um único processo e uma transação por rodada; com várias instâncias da API, usar ShedLock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Expiracao {

    /** Pontos perdidos pela instituição que reserva e não retira (no-show). */
    static final int PENALIDADE_NO_SHOW = 10;

    /** UC06 6a: depois do prazo, o doador ainda pode autorizar a entrega por este tempo antes de a reserva cair. */
    static final Duration TOLERANCIA = Duration.ofMinutes(15);

    private final ReservaRepository reservas;
    private final LoteRepository lotes;
    private final AuditoriaRepository auditoria;
    private final Notificador notificador;
    private final GatewayPagamento gateway;

    @Scheduled(cron = "${app.expiracao.cron}")
    @Transactional
    public void expirar() {
        Instant agora = Instant.now();

        var vencidas = reservas.findByStatusAndPrazoRetiradaBefore(Reserva.Status.PENDENTE, agora.minus(TOLERANCIA));
        for (Reserva r : vencidas) {
            r.expirar();
            r.getInstituicao().setScore(r.getInstituicao().getScore() - PENALIDADE_NO_SHOW);
            if (r.getPagamento() != null) gateway.estornar(r.getPagamento(), r.getPagamento().getSaldo()); // não levou, não paga
            auditoria.save(new Auditoria(null, Auditoria.Acao.RESERVA_EXPIRADA, "RESERVA", r.getId(),
                    "Não retirada até " + r.getPrazoRetirada()));
            String prazo = Notificador.DATA_HORA.format(r.getPrazoRetirada());
            notificador.notificar(r.getInstituicao(), "Reserva expirada", "A reserva de %s expirou em %s sem retirada; você perdeu %d pontos de reputação.%s"
                    .formatted(r.getLote().getNome(), prazo, PENALIDADE_NO_SHOW,
                            r.getPagamento() == null ? "" : " O valor pago foi estornado."), "/reservas");
            notificador.notificar(r.getLote().getEstabelecimento(), "Reserva expirada", "%s não retirou %s até %s; o lote voltou às buscas."
                    .formatted(r.getInstituicao().getNome(), r.getLote().getNome(), prazo), "/lotes/" + r.getLote().getId());
        }

        // depois das reservas: um lote devolvido já fora da janela expira na mesma rodada
        var encerrados = lotes.findByStatusAndRetiradaFimBefore(Lote.Status.DISPONIVEL, agora);
        for (Lote l : encerrados) {
            l.setStatus(Lote.Status.EXPIRADO);
            auditoria.save(new Auditoria(null, Auditoria.Acao.LOTE_EXPIRADO, "LOTE", l.getId(), l.getNome()));
            notificador.notificar(l.getEstabelecimento(), "Lote expirado", "%s saiu das buscas porque a janela de retirada acabou."
                    .formatted(l.getNome()), "/lotes/" + l.getId()); // UC04 6b
        }

        if (!vencidas.isEmpty() || !encerrados.isEmpty()) {
            log.info("Expiração: {} reserva(s) e {} lote(s)", vencidas.size(), encerrados.size());
        }
    }
}
