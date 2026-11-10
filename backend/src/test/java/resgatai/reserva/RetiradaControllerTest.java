package resgatai.reserva;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import resgatai.ApiTest;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetiradaControllerTest extends ApiTest {

    @Autowired
    EntityManager em;

    String padaria;
    String ong;
    String qrCode;
    String pin;
    Integer loteId;

    @BeforeEach
    void setUp() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        postJson("/auth/registro", PADARIA);
        padaria = login("padaria@teste.com", "senha12345");
        Integer ongId = json(postJson("/auth/registro", ONG), "$.id");
        perform(post("/admin/usuarios/" + ongId + "/aprovar"), admin).andExpect(status().isOk());
        ong = login("ong@teste.com", "senha12345");

        loteId = json(perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria), "$.id");
        var reserva = perform(post("/lotes/" + loteId + "/reserva"), ong).andExpect(status().isCreated());
        qrCode = json(reserva, "$.qrCode");
        pin = json(reserva, "$.pin");
    }

    @Test
    @DisplayName("UC06: doador lê o QR Code, confere, valida a entrega e o código não pode ser reutilizado")
    void validarPorQrCode() throws Exception {
        perform(post("/retiradas/conferencia").content(codigo(qrCode)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instituicaoNome").value("ONG Teste"))
                .andExpect(jsonPath("$.lote.nome").value("Pão francês"))
                .andExpect(jsonPath("$.pin").doesNotExist());

        perform(post("/retiradas").content(codigo(qrCode)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLETADA"))
                .andExpect(jsonPath("$.lote.status").value("COLETADO"))
                .andExpect(jsonPath("$.quantidadeEntregue").value(50))
                .andExpect(jsonPath("$.validadoEm").exists())
                .andExpect(jsonPath("$.atrasada").value(false));

        // 5b: código invalidado após o uso
        perform(post("/retiradas").content(codigo(qrCode)), padaria)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("já registrada")))
                .andExpect(jsonPath("$.detail").value(containsString("Padaria Central")));

        // passo 11: reputação das duas partes e impacto da plataforma
        perform(get("/auth/me"), padaria).andExpect(jsonPath("$.score").value(100 + ReservaService.PONTOS_RETIRADA));
        perform(get("/auth/me"), ong).andExpect(jsonPath("$.score").value(100 + ReservaService.PONTOS_RETIRADA));
        perform(get("/impacto"), ong)
                .andExpect(jsonPath("$.plataforma.retiradas").value(1))
                .andExpect(jsonPath("$.plataforma.quantidades.UNIDADE").value(50))
                .andExpect(jsonPath("$.meu.retiradas").value(1));
        perform(get("/admin/auditoria"), login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(jsonPath("$.content[0].acao").value("RETIRADA_VALIDADA"));
    }

    @Test
    @DisplayName("UC06 4a: PIN digitado com entrega parcial soma ao impacto só a quantidade entregue")
    void validarPorPinComDivergencia() throws Exception {
        perform(post("/retiradas").content("{\"codigo\":\"%s\",\"quantidadeEntregue\":60}".formatted(pin)), padaria)
                .andExpect(status().isBadRequest());

        perform(post("/retiradas").content("{\"codigo\":\"%s\",\"quantidadeEntregue\":30}".formatted(pin)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeEntregue").value(30));
        perform(get("/impacto"), padaria).andExpect(jsonPath("$.meu.quantidades.UNIDADE").value(30));
    }

    @Test
    @DisplayName("UC06 5a: código inválido, de outro doador ou validado por outro perfil não altera o lote")
    void codigoInvalido() throws Exception {
        String outroPin = "%04d".formatted((Integer.parseInt(pin) + 1) % 10_000);
        perform(post("/retiradas").content(codigo(outroPin)), padaria).andExpect(status().isNotFound());
        perform(post("/retiradas").content(codigo(qrCode.substring(0, qrCode.length() - 4) + outroPin)), padaria)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Código inválido ou inexistente"));

        postJson("/auth/registro", PADARIA.replace("11.444.777/0001-61", "11.555.666/0001-22").replace("padaria@", "outra@"));
        perform(post("/retiradas").content(codigo(qrCode)), login("outra@teste.com", "senha12345"))
                .andExpect(status().isNotFound());
        perform(post("/retiradas").content(codigo(qrCode)), ong).andExpect(status().isForbidden());

        perform(get("/lotes/" + loteId), padaria).andExpect(jsonPath("$.status").value("RESERVADO_PENDENTE"));
    }

    @Test
    @DisplayName("UC06 6a: fora do prazo, a entrega só é registrada se o doador autorizar, sem pontuar a instituição")
    void foraDoPrazo() throws Exception {
        em.createQuery("update Reserva r set r.prazoRetirada = :prazo")
                .setParameter("prazo", Instant.now().minusSeconds(60)).executeUpdate();
        em.clear();

        perform(post("/retiradas").content(codigo(pin)), padaria)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("autorize a entrega fora do prazo")));

        perform(post("/retiradas").content("{\"codigo\":\"%s\",\"autorizarAtraso\":true}".formatted(pin)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atrasada").value(true));
        perform(get("/auth/me"), ong).andExpect(jsonPath("$.score").value(100));
    }

    @Test
    @DisplayName("UC06 7a: doador cancela a retirada por divergência, com o motivo")
    void cancelarPorDivergencia() throws Exception {
        perform(post("/retiradas/cancelamento").content("{\"codigo\":\"%s\"}".formatted(qrCode)), padaria)
                .andExpect(status().isBadRequest());

        perform(post("/retiradas/cancelamento").content("{\"codigo\":\"%s\",\"motivo\":\"Pães mofados\"}".formatted(qrCode)), padaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.lote.status").value("CANCELADO"));

        perform(post("/retiradas").content(codigo(pin)), padaria)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Pães mofados")));
        perform(get("/impacto"), padaria).andExpect(jsonPath("$.plataforma.retiradas").value(0));
    }

    static String codigo(String codigo) {
        return "{\"codigo\":\"%s\"}".formatted(codigo);
    }
}
