package resgatai.pagamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;

/**
 * Operadora de pagamento simulada (RF04 aceita pagamento simulado). PIX é aprovado na hora; cartão segue as regras
 * dos cartões de teste das operadoras: número fora do Luhn, cartão vencido ou terminado em 0002 são recusados (402).
 * ponytail: simulador síncrono; um PSP real (Mercado Pago, Stripe) entra em cobrar/estornar, com estorno
 * compensatório se a transação da reserva falhar depois da cobrança.
 */
@Slf4j
@Component
public class GatewayPagamento {

    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    public record Cartao(@NotBlank @Pattern(regexp = "\\d{13,19}", message = "informe só os dígitos do cartão") String numero,
                         @NotBlank String titular,
                         @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "use MM/AA") String validade,
                         @NotBlank @Pattern(regexp = "\\d{3,4}") String cvv) {}

    public Pagamento cobrar(BigDecimal valor, Pagamento.Metodo metodo, Cartao cartao) {
        if (metodo == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Lote com preço: informe a forma de pagamento (PIX ou CARTAO)");
        }
        String finalCartao = null;
        if (metodo == Pagamento.Metodo.CARTAO) {
            if (cartao == null) throw new ResponseStatusException(BAD_REQUEST, "Informe os dados do cartão");
            recusarSe(!luhn(cartao.numero()), "número inválido");
            recusarSe(YearMonth.parse(cartao.validade(), MM_AA).isBefore(YearMonth.now(ZoneId.of("America/Sao_Paulo"))), "cartão vencido");
            recusarSe(cartao.numero().endsWith("0002"), "saldo insuficiente");
            finalCartao = cartao.numero().substring(cartao.numero().length() - 4);
        }
        String transacao = "SIM-" + UUID.randomUUID();
        log.info("Pagamento simulado {} aprovado: {} via {}", transacao, valor, metodo);
        return new Pagamento(metodo, valor, finalCartao, transacao);
    }

    /** Devolve parte do valor pago (até o saldo). */
    public void estornar(Pagamento p, BigDecimal valor) {
        BigDecimal estorno = valor.min(p.getSaldo());
        if (estorno.signum() <= 0) return;
        log.info("Estorno simulado de {} na transação {}", estorno, p.getTransacao());
        p.registrarEstorno(estorno);
    }

    private static void recusarSe(boolean recusado, String motivo) {
        if (recusado) throw new ResponseStatusException(PAYMENT_REQUIRED, "Pagamento recusado pela operadora: " + motivo);
    }

    /** Dígito verificador de cartões (ISO/IEC 7812). */
    static boolean luhn(String numero) {
        int soma = 0;
        for (int i = 0; i < numero.length(); i++) {
            int d = numero.charAt(numero.length() - 1 - i) - '0';
            if (i % 2 == 1) d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            soma += d;
        }
        return soma % 10 == 0;
    }
}
