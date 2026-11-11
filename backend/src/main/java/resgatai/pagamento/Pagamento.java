package resgatai.pagamento;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** RF04: liquidação do lote na reserva; guarda só o final do cartão, nunca o número. Nulo em lote doado. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pagamento {

    public enum Metodo { PIX, CARTAO }

    public enum Status { APROVADO, ESTORNADO_PARCIAL, ESTORNADO }

    @Enumerated(EnumType.STRING)
    @Column(name = "pagamento_metodo")
    private Metodo metodo;

    @Column(name = "pagamento_valor")
    private BigDecimal valor;

    @Column(name = "pagamento_valor_estornado")
    private BigDecimal valorEstornado;

    @Column(name = "pagamento_final_cartao")
    private String finalCartao;

    @Column(name = "pagamento_transacao")
    private String transacao;

    Pagamento(Metodo metodo, BigDecimal valor, String finalCartao, String transacao) {
        this.metodo = metodo;
        this.valor = valor;
        this.valorEstornado = BigDecimal.ZERO;
        this.finalCartao = finalCartao;
        this.transacao = transacao;
    }

    /** Quanto ainda pode ser devolvido. */
    public BigDecimal getSaldo() {
        return valor.subtract(valorEstornado);
    }

    public Status getStatus() {
        if (valorEstornado.signum() == 0) return Status.APROVADO;
        return getSaldo().signum() == 0 ? Status.ESTORNADO : Status.ESTORNADO_PARCIAL;
    }

    void registrarEstorno(BigDecimal valor) {
        this.valorEstornado = valorEstornado.add(valor);
    }
}
