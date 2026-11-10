package resgatai.reserva;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import resgatai.lote.Lote;
import resgatai.pagamento.Pagamento;
import resgatai.usuario.Instituicao;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/** Reserva de um lote por uma instituição (UC04), com o PIN usado na validação da retirada. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reserva {

    public enum Status { PENDENTE, COLETADA, CANCELADA, EXPIRADA }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Lote lote;

    @ManyToOne(optional = false)
    private Instituicao instituicao;

    private String pin;
    private Instant prazoRetirada;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDENTE;

    private Instant criadoEm = Instant.now();

    /** UC06: preenchidos na validação da retirada; o responsável é o doador do lote. */
    private Instant validadoEm;
    private BigDecimal quantidadeEntregue;
    private boolean atrasada;
    private String motivoCancelamento;

    /** RF04: liquidado na reserva; nulo quando o lote é doado (preço zero). */
    @Embedded
    private Pagamento pagamento;

    /**
     * UC04 passo 8: gera o PIN e define o prazo limite pela janela de retirada informada pelo doador.
     * O prazo é a metade do tempo restante da janela, para que, se a instituição não aparecer, o lote
     * volte às buscas com tempo de ser retirado por outra (Estado 4 da descrição do projeto).
     */
    public Reserva(Lote lote, Instituicao instituicao, Pagamento pagamento) {
        this.lote = lote;
        this.instituicao = instituicao;
        this.pagamento = pagamento;
        this.pin = "%04d".formatted(RANDOM.nextInt(10_000));
        Instant inicio = lote.getRetiradaInicio().isAfter(criadoEm) ? lote.getRetiradaInicio() : criadoEm;
        this.prazoRetirada = inicio.plus(Duration.between(inicio, lote.getRetiradaFim()).dividedBy(2));
    }

    /** UC06 passos 9-10: sair de PENDENTE invalida o código para novos usos. */
    void coletar(BigDecimal quantidadeEntregue, boolean atrasada) {
        this.status = Status.COLETADA;
        this.validadoEm = Instant.now();
        this.quantidadeEntregue = quantidadeEntregue;
        this.atrasada = atrasada;
        lote.setStatus(Lote.Status.COLETADO);
    }

    /** UC06 7a: divergência entre o lote e o que está sendo entregue. */
    void cancelar(String motivo) {
        this.status = Status.CANCELADA;
        this.validadoEm = Instant.now();
        this.motivoCancelamento = motivo;
        lote.setStatus(Lote.Status.CANCELADO);
    }

    /** Expiração por timeout: a instituição não retirou no prazo e o lote volta a ficar disponível. */
    void expirar() {
        this.status = Status.EXPIRADA;
        lote.setStatus(Lote.Status.DISPONIVEL);
    }

    /** Conteúdo do QR Code, desenhado pelo front; leva a reserva e o PIN para o doador validar com uma leitura. */
    public String getQrCode() {
        return "RESGATAI-" + id + "-" + pin;
    }
}
