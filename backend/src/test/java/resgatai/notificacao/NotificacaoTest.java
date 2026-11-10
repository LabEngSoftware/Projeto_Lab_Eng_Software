package resgatai.notificacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import resgatai.ApiTest;
import resgatai.geo.Coordenadas;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacaoTest extends ApiTest {

    /** ~8 km ao sul do Mackenzie: fora do raio de lote urgente (5 km), dentro do de urgência média (10 km). */
    static final String VILA_MARIANA = "Rua Domingos de Morais, 2000";
    static final String CAMPINAS = "Av. Francisco Glicério, 1000, Campinas";

    @MockitoBean
    JavaMailSender mail;

    @Autowired
    Notificador notificador;

    String admin;
    String padaria;

    @BeforeEach
    void setUp() throws Exception {
        when(geocodificador.geocodificar(VILA_MARIANA)).thenReturn(new Coordenadas(-23.6194, -46.6520));
        when(geocodificador.geocodificar(CAMPINAS)).thenReturn(new Coordenadas(-22.9056, -47.0608));
        admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        postJson("/auth/registro", PADARIA);
        padaria = login("padaria@teste.com", "senha12345");
    }

    String ong(String nome, String cnpj, String endereco, boolean aprovar) throws Exception {
        String email = nome + "@teste.com";
        Integer id = json(postJson("/auth/registro", ONG.replace("ONG Teste", nome).replace("11.222.333/0001-81", cnpj)
                .replace("ong@teste.com", email).replace("Rua da Consolação, 930", endereco)), "$.id");
        if (aprovar) perform(post("/admin/usuarios/" + id + "/aprovar"), admin).andExpect(status().isOk());
        return login(email, "senha12345");
    }

    @Test
    @DisplayName("UC01 passo 8: o link de primeiro acesso vai por e-mail e não volta para o admin")
    void primeiroAcessoPorEmail() throws Exception {
        perform(post("/admin/usuarios").content(ONG), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkPrimeiroAcesso").doesNotExist());

        var email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(email.capture());
        assertThat(email.getValue().getTo()).containsExactly("ong@teste.com");
        String texto = email.getValue().getText();
        String token = texto.substring(texto.indexOf("token=") + 6).strip();
        postJson("/auth/definir-senha", "{\"token\":\"%s\",\"senha\":\"nova12345\",\"aceiteLgpd\":true}".formatted(token))
                .andExpect(status().isNoContent());

        // o link não fica na caixa de entrada nem na fila de e-mails
        perform(get("/notificacoes"), login("ong@teste.com", "nova12345")).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("UC01 passo 8: se o e-mail falhar, o link volta na resposta para o admin repassar")
    void primeiroAcessoSemEmail() throws Exception {
        doThrow(new MailSendException("SMTP fora do ar")).when(mail).send(any(SimpleMailMessage.class));

        perform(post("/admin/usuarios").content(ONG), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkPrimeiroAcesso").value(containsString("/definir-senha?token=")));
    }

    @Test
    @DisplayName("CU03: novo lote notifica só instituições aprovadas no raio da sua urgência")
    void lotesProximos() throws Exception {
        String perto = ong("perto", "11.222.333/0001-81", "Rua da Consolação, 930", true);
        String medio = ong("medio", "11.555.666/0001-22", VILA_MARIANA, true);
        String longe = ong("longe", "45.723.174/0001-10", CAMPINAS, true);
        String pendente = ong("pendente", "34.028.316/0001-03", "Rua da Consolação, 930", false);

        perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria); // ALTA: 5 km
        perform(get("/notificacoes"), perto)
                .andExpect(jsonPath("$.content[0].titulo").value("Novo lote perto de você"))
                .andExpect(jsonPath("$.content[0].mensagem").value(containsString("Padaria Central oferece Pão francês (50 UNIDADE) a 0,0 km")))
                .andExpect(jsonPath("$.content[0].link").value(startsWith("/lotes/")))
                .andExpect(jsonPath("$.content[0].lida").value(false));
        perform(get("/notificacoes"), medio).andExpect(jsonPath("$.content.length()").value(0));

        perform(post("/lotes").content(lote("Bolo", "PADARIA", 0, Duration.ofHours(12), null)), padaria); // MEDIA: 10 km
        perform(get("/notificacoes"), medio).andExpect(jsonPath("$.content[0].mensagem").value(containsString("Bolo")));
        perform(get("/notificacoes"), longe).andExpect(jsonPath("$.content.length()").value(0));
        perform(get("/notificacoes"), pendente).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("UC04 passo 11 e UC06 passo 12: doador sabe da reserva, instituição da retirada; cada um lê só as suas")
    void reservaERetirada() throws Exception {
        String ong = ong("ong", "11.222.333/0001-81", "Rua da Consolação, 930", true);
        Integer loteId = json(perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria), "$.id");
        String qrCode = json(perform(post("/lotes/" + loteId + "/reserva"), ong), "$.qrCode");

        var reservado = perform(get("/notificacoes"), padaria)
                .andExpect(jsonPath("$.content[0].titulo").value("Lote reservado"))
                .andExpect(jsonPath("$.content[0].mensagem").value(containsString("ong reservou Pão francês")));
        Integer id = json(reservado, "$.content[0].id");

        perform(put("/notificacoes/" + id + "/lida"), ong).andExpect(status().isNotFound());
        perform(put("/notificacoes/" + id + "/lida"), padaria).andExpect(status().isNoContent());
        perform(get("/notificacoes"), padaria).andExpect(jsonPath("$.content[0].lida").value(true));

        perform(post("/retiradas").content("{\"codigo\":\"%s\"}".formatted(qrCode)), padaria).andExpect(status().isOk());
        perform(get("/notificacoes"), ong)
                .andExpect(jsonPath("$.content[0].titulo").value("Retirada concluída"))
                .andExpect(jsonPath("$.content[0].mensagem").value(containsString("50 UNIDADE de Pão francês")));
    }

    @Test
    @DisplayName("RF07: o worker envia as notificações por e-mail uma vez; com SMTP fora do ar, elas ficam na fila")
    void filaDeEmails() throws Exception {
        ong("ong", "11.222.333/0001-81", "Rua da Consolação, 930", true);
        doThrow(new MailSendException("SMTP fora do ar")).when(mail).send(any(SimpleMailMessage.class));
        perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria);

        notificador.enviarEmails();
        reset(mail);
        notificador.enviarEmails();

        var email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(email.capture());
        assertThat(email.getValue().getTo()).containsExactly("ong@teste.com");
        assertThat(email.getValue().getSubject()).isEqualTo("ResgatAI: Novo lote perto de você");
        assertThat(email.getValue().getText()).contains("http://localhost:3000/lotes/");

        notificador.enviarEmails();
        verifyNoMoreInteractions(mail);
    }
}
