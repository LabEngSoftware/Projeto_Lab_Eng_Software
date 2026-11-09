package resgatai.reserva;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import resgatai.ApiTest;
import resgatai.lote.LoteRepository;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservaControllerTest extends ApiTest {

    @Autowired
    LoteRepository lotes;

    String admin;
    String padaria;
    String ong;
    Integer ongId;
    Integer loteId;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        postJson("/auth/registro", PADARIA);
        padaria = login("padaria@teste.com", "senha12345");
        ongId = instituicaoAprovada(ONG);
        ong = login("ong@teste.com", "senha12345");
        loteId = json(perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria), "$.id");
    }

    Integer instituicaoAprovada(String registro) throws Exception {
        Integer id = json(postJson("/auth/registro", registro), "$.id");
        perform(post("/admin/usuarios/" + id + "/aprovar"), admin).andExpect(status().isOk());
        return id;
    }

    @Test
    @DisplayName("UC04: instituição aprovada reserva, recebe PIN/QR Code e o lote sai da busca")
    void reservar() throws Exception {
        perform(post("/lotes/" + loteId + "/reserva"), ong)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin").value(matchesPattern("\\d{4}")))
                .andExpect(jsonPath("$.qrCode").value(containsString("RESGATAI-")))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.lote.status").value("RESERVADO_PENDENTE"))
                .andExpect(jsonPath("$.lote.enderecoRetirada").value("Rua da Consolação, 930"));

        perform(get("/lotes"), ong).andExpect(jsonPath("$.content.length()").value(0));
        perform(get("/reservas"), ong).andExpect(jsonPath("$.content[0].pin").exists());

        // o doador vê quem reservou, mas não o PIN: é a instituição que o apresenta na retirada
        perform(get("/reservas"), padaria)
                .andExpect(jsonPath("$.content[0].instituicaoNome").value("ONG Teste"))
                .andExpect(jsonPath("$.content[0].pin").doesNotExist());
        perform(get("/lotes/meus"), padaria).andExpect(jsonPath("$.content[0].status").value("RESERVADO_PENDENTE"));
    }

    @Test
    @DisplayName("UC04 6a: lote já reservado retorna 409 para a segunda instituição")
    void loteJaReservado() throws Exception {
        instituicaoAprovada(ONG.replace("11.222.333/0001-81", "11.555.666/0001-22")
                .replace("ong@", "outra@"));
        perform(post("/lotes/" + loteId + "/reserva"), ong).andExpect(status().isCreated());

        perform(post("/lotes/" + loteId + "/reserva"), login("outra@teste.com", "senha12345"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("outra instituição")));
    }

    @Test
    @DisplayName("UC04 6b e RF06: lote fora da janela de retirada, instituição pendente e outros perfis não reservam")
    void reservaNegada() throws Exception {
        postJson("/auth/registro", ONG.replace("11.222.333/0001-81", "45.723.174/0001-10").replace("ong@", "pendente@"));
        perform(post("/lotes/" + loteId + "/reserva"), login("pendente@teste.com", "senha12345"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("aprovação")));

        perform(post("/lotes/" + loteId + "/reserva"), padaria).andExpect(status().isForbidden());
        perform(post("/lotes/999999/reserva"), ong).andExpect(status().isNotFound());

        lotes.findById(loteId.longValue()).orElseThrow().setRetiradaFim(Instant.now().minusSeconds(60));
        perform(post("/lotes/" + loteId + "/reserva"), ong)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("janela de retirada")));
    }

    @Test
    @DisplayName("UC01 3c: usuário com reserva em andamento não pode ser inativado")
    void inativarComReserva() throws Exception {
        perform(post("/lotes/" + loteId + "/reserva"), ong).andExpect(status().isCreated());

        perform(delete("/admin/usuarios/" + ongId), admin).andExpect(status().isConflict());
    }
}
