package resgatai;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import resgatai.geo.Coordenadas;
import resgatai.geo.Geocodificador;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de API: sobe a aplicação com H2 e desfaz as alterações no banco ao fim de cada teste.
 * A geocodificação é simulada e, por padrão, todo endereço cai no Mackenzie.
 * Os workers ficam desligados (os testes os chamam diretamente) e não há SMTP: quem testa e-mail simula o JavaMailSender.
 */
@SpringBootTest(properties = {"app.expiracao.cron=-", "app.notificacao.cron=-",
        "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"})
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTest {

    protected static final String ADMIN_EMAIL = "admin@resgatai.local";
    protected static final String ADMIN_SENHA = "admin12345";

    protected static final String ONG = """
            {"perfil":"INSTITUICAO","nome":"ONG Teste","documento":"11.222.333/0001-81",
             "email":"ong@teste.com","senha":"senha12345","telefone":"11999999999",
             "endereco":"Rua da Consolação, 930","aceiteLgpd":true}""";

    protected static final String PADARIA = ONG.replace("INSTITUICAO", "ESTABELECIMENTO")
            .replace("11.222.333/0001-81", "11.444.777/0001-61")
            .replace("ong@", "padaria@")
            .replace("ONG Teste", "Padaria Central");

    protected static final Coordenadas MACKENZIE = new Coordenadas(-23.5475, -46.6520);

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected Geocodificador geocodificador;

    @BeforeEach
    void geocodificacaoPadrao() {
        when(geocodificador.geocodificar(anyString())).thenReturn(MACKENZIE);
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions perform(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }

    protected String login(String email, String senha) throws Exception {
        String body = postJson("/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** Lote cuja janela de retirada vai de agora até a validade. */
    protected static String lote(String nome, String categoria, int preco, Duration validade, String enderecoRetirada) {
        Instant agora = Instant.now();
        return """
                {"nome":"%s","categoria":"%s","quantidade":50,"unidade":"UNIDADE","preco":%d,
                 "validade":"%s","retiradaInicio":"%s","retiradaFim":"%s","enderecoRetirada":%s}"""
                .formatted(nome, categoria, preco, agora.plus(validade), agora, agora.plus(validade),
                        enderecoRetirada == null ? "null" : "\"" + enderecoRetirada + "\"");
    }

    protected <T> T json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
