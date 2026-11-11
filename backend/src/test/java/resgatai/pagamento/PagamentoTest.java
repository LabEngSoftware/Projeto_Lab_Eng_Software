package resgatai.pagamento;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import resgatai.ApiTest;
import resgatai.reserva.Expiracao;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagamentoTest extends ApiTest {

    static final String CARTAO = """
            {"metodo":"CARTAO","cartao":{"numero":"%s","titular":"ONG Teste","validade":"%s","cvv":"123"}}""";

    @Autowired
    Expiracao expiracao;

    @Autowired
    EntityManager em;

    String padaria;
    String ong;
    Integer loteId;

    @BeforeEach
    void setUp() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        postJson("/auth/registro", PADARIA);
        padaria = login("padaria@teste.com", "senha12345");
        Integer ongId = json(postJson("/auth/registro", ONG), "$.id");
        perform(post("/admin/usuarios/" + ongId + "/aprovar"), admin).andExpect(status().isOk());
        ong = login("ong@teste.com", "senha12345");
        loteId = json(perform(post("/lotes").content(lote("Pão francês", "PADARIA", 15, Duration.ofHours(3), null)), padaria), "$.id");
    }

    String reservar(String corpo) throws Exception {
        return json(perform(post("/lotes/" + loteId + "/reserva").content(corpo), ong).andExpect(status().isCreated()), "$.qrCode");
    }

    static String cartao(String numero) {
        return CARTAO.formatted(numero, "12/99");
    }

    @Test
    @DisplayName("RF04: lote com preço é pago no cartão na reserva; só o final do cartão é guardado")
    void pagarComCartao() throws Exception {
        perform(post("/lotes/" + loteId + "/reserva"), ong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("forma de pagamento")));

        perform(post("/lotes/" + loteId + "/reserva").content(cartao("4111111111111111")), ong)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagamento.metodo").value("CARTAO"))
                .andExpect(jsonPath("$.pagamento.valor").value(15))
                .andExpect(jsonPath("$.pagamento.status").value("APROVADO"))
                .andExpect(jsonPath("$.pagamento.finalCartao").value("1111"))
                .andExpect(jsonPath("$.pagamento.transacao").value(containsString("SIM-")));
        perform(get("/reservas"), padaria).andExpect(jsonPath("$.content[0].pagamento.valor").value(15));
    }

    @Test
    @DisplayName("RF04: cartão recusado, vencido ou inválido não reserva e o lote segue disponível")
    void pagamentoRecusado() throws Exception {
        for (String corpo : new String[]{cartao("4000000000000002"), cartao("4111111111111112"), CARTAO.formatted("4111111111111111", "01/20")}) {
            perform(post("/lotes/" + loteId + "/reserva").content(corpo), ong)
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.detail").value(containsString("recusado")));
        }
        perform(post("/lotes/" + loteId + "/reserva").content(CARTAO.formatted("4111 1111", "13/30")), ong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['cartao.numero']").exists())
                .andExpect(jsonPath("$.erros['cartao.validade']").exists());

        perform(get("/lotes/" + loteId), ong).andExpect(jsonPath("$.status").value("DISPONIVEL"));
        perform(get("/reservas"), ong).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("RF04: PIX é aprovado na hora; lote doado não gera pagamento")
    void pixEDoacao() throws Exception {
        perform(post("/lotes/" + loteId + "/reserva").content("{\"metodo\":\"PIX\"}"), ong)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagamento.metodo").value("PIX"))
                .andExpect(jsonPath("$.pagamento.finalCartao").doesNotExist());

        Integer doado = json(perform(post("/lotes").content(lote("Bolo", "PADARIA", 0, Duration.ofHours(3), null)), padaria), "$.id");
        perform(post("/lotes/" + doado + "/reserva"), ong)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagamento").doesNotExist());
    }

    @Test
    @DisplayName("UC06 4a: na entrega parcial a instituição paga só o que recebeu")
    void estornoParcial() throws Exception {
        String qrCode = reservar("{\"metodo\":\"PIX\"}");

        perform(post("/retiradas").content("{\"codigo\":\"%s\",\"quantidadeEntregue\":30}".formatted(qrCode)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamento.valorEstornado").value(6)) // 15 × 20/50 não entregues
                .andExpect(jsonPath("$.pagamento.saldo").value(9))
                .andExpect(jsonPath("$.pagamento.status").value("ESTORNADO_PARCIAL"));
    }

    @Test
    @DisplayName("UC06 7a: retirada cancelada por divergência estorna o valor pago")
    void estornoNoCancelamento() throws Exception {
        String qrCode = reservar(cartao("4111111111111111"));

        perform(post("/retiradas/cancelamento").content("{\"codigo\":\"%s\",\"motivo\":\"Pães mofados\"}".formatted(qrCode)), padaria)
                .andExpect(jsonPath("$.pagamento.valorEstornado").value(15))
                .andExpect(jsonPath("$.pagamento.status").value("ESTORNADO"));
        perform(get("/notificacoes"), ong).andExpect(jsonPath("$.content[0].mensagem").value(containsString("estornado")));
    }

    @Test
    @DisplayName("Timeout: reserva expirada sem retirada estorna o valor pago")
    void estornoNaExpiracao() throws Exception {
        reservar("{\"metodo\":\"PIX\"}");
        em.createQuery("update Reserva r set r.prazoRetirada = :prazo")
                .setParameter("prazo", Instant.now().minus(Duration.ofHours(1))).executeUpdate();
        em.clear();

        expiracao.expirar();
        perform(get("/reservas"), ong)
                .andExpect(jsonPath("$.content[0].status").value("EXPIRADA"))
                .andExpect(jsonPath("$.content[0].pagamento.status").value("ESTORNADO"));
    }
}
