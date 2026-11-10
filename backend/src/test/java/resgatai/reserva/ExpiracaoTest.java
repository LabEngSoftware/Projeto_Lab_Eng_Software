package resgatai.reserva;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import resgatai.ApiTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpiracaoTest extends ApiTest {

    @Autowired
    Expiracao expiracao;

    @Autowired
    EntityManager em;

    String admin;
    String padaria;
    String ong;
    Integer loteId;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        postJson("/auth/registro", PADARIA);
        padaria = login("padaria@teste.com", "senha12345");
        ong = instituicaoAprovada(ONG, "ong@teste.com");
        loteId = json(perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(4), null)), padaria), "$.id");
    }

    String instituicaoAprovada(String registro, String email) throws Exception {
        Integer id = json(postJson("/auth/registro", registro), "$.id");
        perform(post("/admin/usuarios/" + id + "/aprovar"), admin).andExpect(status().isOk());
        return login(email, "senha12345");
    }

    /** Simula a passagem do tempo movendo o prazo da reserva para o passado. */
    void prazoHa(Duration atraso) {
        em.createQuery("update Reserva r set r.prazoRetirada = :prazo")
                .setParameter("prazo", Instant.now().minus(atraso)).executeUpdate();
        em.clear();
    }

    @Test
    @DisplayName("Estado 4: o prazo de retirada é a metade do tempo restante da janela")
    void prazoMetadeDaJanela() throws Exception {
        String prazo = json(perform(post("/lotes/" + loteId + "/reserva"), ong), "$.prazoRetirada");

        assertThat(Duration.between(Instant.now(), Instant.parse(prazo)))
                .isBetween(Duration.ofMinutes(119), Duration.ofMinutes(120));
    }

    @Test
    @DisplayName("Timeout: reserva não retirada expira, penaliza a instituição e devolve o lote às buscas")
    void reservaExpirada() throws Exception {
        String qrCode = json(perform(post("/lotes/" + loteId + "/reserva"), ong), "$.qrCode");

        // dentro da tolerância o doador ainda pode autorizar a entrega atrasada (UC06 6a)
        prazoHa(Expiracao.TOLERANCIA.minusMinutes(1));
        expiracao.expirar();
        perform(get("/reservas"), ong).andExpect(jsonPath("$.content[0].status").value("PENDENTE"));

        prazoHa(Expiracao.TOLERANCIA.plusMinutes(1));
        expiracao.expirar();
        perform(get("/reservas"), ong).andExpect(jsonPath("$.content[0].status").value("EXPIRADA"));
        perform(get("/auth/me"), ong).andExpect(jsonPath("$.score").value(100 - Expiracao.PENALIDADE_NO_SHOW));
        perform(get("/admin/auditoria"), admin)
                .andExpect(jsonPath("$.content[0].acao").value("RESERVA_EXPIRADA"))
                .andExpect(jsonPath("$.content[0].autorId").doesNotExist());

        perform(post("/retiradas").content("{\"codigo\":\"%s\",\"autorizarAtraso\":true}".formatted(qrCode)), padaria)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("expirada")));

        String outra = instituicaoAprovada(ONG.replace("11.222.333/0001-81", "11.555.666/0001-22").replace("ong@", "outra@"), "outra@teste.com");
        perform(get("/lotes"), outra).andExpect(jsonPath("$.content[0].id").value(loteId));
        perform(post("/lotes/" + loteId + "/reserva"), outra).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RNF02 e UC04 6b: lote cuja janela de retirada acabou passa a Expirado e não pode ser reservado")
    void loteExpirado() throws Exception {
        em.createQuery("update Lote l set l.retiradaFim = :fim").setParameter("fim", Instant.now().minusSeconds(60)).executeUpdate();
        em.clear();

        expiracao.expirar();
        perform(get("/lotes/meus"), padaria).andExpect(jsonPath("$.content[0].status").value("EXPIRADO"));
        perform(post("/lotes/" + loteId + "/reserva"), ong)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("janela de retirada")));
    }

    @Test
    @DisplayName("Reserva que expira depois do fim da janela devolve o lote já como Expirado, na mesma rodada")
    void reservaELoteExpirados() throws Exception {
        perform(post("/lotes/" + loteId + "/reserva"), ong).andExpect(status().isCreated());
        em.createQuery("update Lote l set l.retiradaFim = :fim").setParameter("fim", Instant.now().minusSeconds(60)).executeUpdate();
        prazoHa(Expiracao.TOLERANCIA.plusMinutes(1));

        expiracao.expirar();
        perform(get("/lotes/meus"), padaria).andExpect(jsonPath("$.content[0].status").value("EXPIRADO"));
        perform(get("/reservas"), padaria).andExpect(jsonPath("$.content[0].status").value("EXPIRADA"));
    }
}
