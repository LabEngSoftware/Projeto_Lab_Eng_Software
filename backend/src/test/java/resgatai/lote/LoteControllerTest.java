package resgatai.lote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import resgatai.ApiTest;
import resgatai.geo.Coordenadas;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoteControllerTest extends ApiTest {

    static final String CAMPINAS = "Av. Francisco Glicério, 1000, Campinas";

    @Autowired
    LoteRepository lotes;

    Integer padariaId;
    String padaria;
    String ong;

    @BeforeEach
    void setUp() throws Exception {
        when(geocodificador.geocodificar(CAMPINAS)).thenReturn(new Coordenadas(-22.9056, -47.0608));
        padariaId = json(postJson("/auth/registro", PADARIA), "$.id");
        postJson("/auth/registro", ONG);
        padaria = login("padaria@teste.com", "senha12345");
        ong = login("ong@teste.com", "senha12345");
    }

    Integer criar(String body) throws Exception {
        return json(perform(post("/lotes").content(body), padaria).andExpect(status().isCreated()), "$.id");
    }

    @Test
    @DisplayName("UC02: estabelecimento publica lote disponível no próprio endereço, com urgência e registro no log")
    void criarLote() throws Exception {
        perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), padaria)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.urgencia").value("ALTA"))
                .andExpect(jsonPath("$.estabelecimentoNome").value("Padaria Central"))
                .andExpect(jsonPath("$.enderecoRetirada").value("Rua da Consolação, 930"));

        perform(get("/admin/auditoria"), login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(jsonPath("$.content[0].acao").value("LOTE_CRIADO"));

        perform(post("/lotes").content(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null)), ong)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UC02 5a/7a/9a: lote vencido, janela de retirada após a validade e campos ausentes retornam 400")
    void criarInvalido() throws Exception {
        perform(post("/lotes").content(lote("Pão", "PADARIA", 0, Duration.ofHours(-1), null)), padaria)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.validade").value("lotes vencidos não podem ser publicados"));

        String janelaDepoisDaValidade = lote("Pão", "PADARIA", 0, Duration.ofHours(3), null)
                .replaceFirst("\"validade\":\"[^\"]+\"", "\"validade\":\"" + Instant.now().plus(Duration.ofHours(1)) + "\"");
        perform(post("/lotes").content(janelaDepoisDaValidade), padaria)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("janela de retirada")));

        perform(post("/lotes").content("{}"), padaria)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.categoria").exists())
                .andExpect(jsonPath("$.erros.validade").exists());
    }

    @Test
    @DisplayName("UC02 7a: endereço de retirada alternativo vale só para o lote")
    void enderecoAlternativo() throws Exception {
        when(geocodificador.geocodificar("Av. Paulista, 1000, São Paulo")).thenReturn(new Coordenadas(-23.5650, -46.6520));
        Integer id = criar(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), "Av. Paulista, 1000, São Paulo"));

        perform(get("/lotes/" + id), ong)
                .andExpect(jsonPath("$.enderecoRetirada").value("Av. Paulista, 1000, São Paulo"))
                .andExpect(jsonPath("$.distanciaKm").value(1.9));
        perform(get("/auth/me"), padaria).andExpect(jsonPath("$.endereco").value("Rua da Consolação, 930"));
    }

    @Test
    @DisplayName("RF03: busca por raio, categoria, faixa de preço, prazo de vencimento e nome")
    void buscar() throws Exception {
        Integer pao = criar(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null));
        Integer iogurte = criar(lote("Iogurte natural", "LATICINIOS", 5, Duration.ofHours(30), CAMPINAS));

        // raio padrão de 10 km a partir do endereço da ONG
        perform(get("/lotes"), ong)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pao))
                .andExpect(jsonPath("$.content[0].distanciaKm").value(0.0));

        perform(get("/lotes").param("raioKm", "100"), ong)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(pao)) // mais urgente primeiro
                .andExpect(jsonPath("$.content[1].urgencia").value("BAIXA"));

        perform(get("/lotes").param("raioKm", "100").param("categoria", "LATICINIOS"), ong)
                .andExpect(jsonPath("$.content[*].id").value(iogurte));
        perform(get("/lotes").param("raioKm", "100").param("precoMax", "1"), ong)
                .andExpect(jsonPath("$.content[*].id").value(pao));
        perform(get("/lotes").param("raioKm", "100").param("precoMin", "1"), ong)
                .andExpect(jsonPath("$.content[*].id").value(iogurte));
        perform(get("/lotes").param("raioKm", "100").param("venceAte", Instant.now().plus(Duration.ofHours(6)).toString()), ong)
                .andExpect(jsonPath("$.content[*].id").value(pao));
        perform(get("/lotes").param("raioKm", "100").param("nome", "IOGURTE"), ong)
                .andExpect(jsonPath("$.content[*].id").value(iogurte));

        // ponto informado pelo aparelho, no lugar do endereço cadastrado
        perform(get("/lotes").param("latitude", "-22.9056").param("longitude", "-47.0608"), ong)
                .andExpect(jsonPath("$.content[*].id").value(iogurte));

        perform(get("/lotes").param("raioKm", "500"), ong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.raioKm").exists());
    }

    @Test
    @DisplayName("RNF02: lote com janela de retirada encerrada ou de doador inativo some da busca, mas segue em 'meus lotes'")
    void ocultarIndisponiveis() throws Exception {
        Integer encerrado = criar(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null));
        lotes.findById(encerrado.longValue()).orElseThrow().setRetiradaFim(Instant.now().minusSeconds(60));

        perform(get("/lotes"), ong).andExpect(jsonPath("$.content.length()").value(0));
        perform(get("/lotes/meus"), padaria).andExpect(jsonPath("$.content[0].id").value(encerrado));

        criar(lote("Bolo", "PADARIA", 0, Duration.ofHours(3), null));
        perform(get("/lotes"), ong).andExpect(jsonPath("$.content.length()").value(1));

        perform(delete("/admin/usuarios/" + padariaId), login(ADMIN_EMAIL, ADMIN_SENHA)).andExpect(status().isNoContent());
        perform(get("/lotes"), ong).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("RF02: o doador envia a foto do lote, que fica pública para exibição")
    void foto() throws Exception {
        Integer id = criar(lote("Pão francês", "PADARIA", 0, Duration.ofHours(3), null));
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

        mvc.perform(multipart(HttpMethod.PUT, "/lotes/{id}/foto", id)
                        .file(new MockMultipartFile("arquivo", "pao.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + padaria))
                .andExpect(status().isNoContent());

        mvc.perform(get("/lotes/{id}/foto", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(jpeg));
        perform(get("/lotes/" + id), ong).andExpect(jsonPath("$.fotoUrl").value("/lotes/" + id + "/foto"));

        mvc.perform(multipart(HttpMethod.PUT, "/lotes/{id}/foto", id)
                        .file(new MockMultipartFile("arquivo", "x.html", "text/html", "<script>".getBytes()))
                        .header("Authorization", "Bearer " + padaria))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, "/lotes/{id}/foto", id)
                        .file(new MockMultipartFile("arquivo", "pao.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + ong))
                .andExpect(status().isForbidden());
    }
}
