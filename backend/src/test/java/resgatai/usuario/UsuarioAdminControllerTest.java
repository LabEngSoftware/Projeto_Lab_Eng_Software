package resgatai.usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import resgatai.ApiTest;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioAdminControllerTest extends ApiTest {

    static final String PADARIA = """
            {"perfil":"ESTABELECIMENTO","nome":"Padaria Central","documento":"11.444.777/0001-61",
             "email":"padaria@teste.com","telefone":"11988887777","endereco":"Rua Itambé, 45"}""";

    String admin;
    Integer ongId;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        ongId = json(postJson("/auth/registro", ONG), "$.id");
    }

    @Test
    @DisplayName("Rotas /admin exigem perfil ADMIN: 401 sem token e 403 para outros perfis")
    void acessoRestrito() throws Exception {
        mvc.perform(get("/admin/usuarios")).andExpect(status().isUnauthorized());
        perform(get("/admin/usuarios"), login("ong@teste.com", "senha12345")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UC01 principal: admin cadastra usuário, que define a senha pelo link de primeiro acesso")
    void criarEDefinirSenha() throws Exception {
        var criado = perform(post("/admin/usuarios").content(PADARIA), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.perfil").value("ESTABELECIMENTO"))
                .andExpect(jsonPath("$.usuario.documento").value("11444777000161"))
                .andExpect(jsonPath("$.linkPrimeiroAcesso").value(containsString("/definir-senha?token=")));
        String link = json(criado, "$.linkPrimeiroAcesso");
        Integer id = json(criado, "$.usuario.id");
        String token = link.substring(link.indexOf("token=") + 6);

        String definir = "{\"token\":\"%s\",\"senha\":\"nova12345\",\"aceiteLgpd\":true}".formatted(token);
        postJson("/auth/definir-senha", definir).andExpect(status().isNoContent());
        login("padaria@teste.com", "nova12345");

        postJson("/auth/definir-senha", definir).andExpect(status().isBadRequest()); // link de uso único

        perform(get("/admin/auditoria").param("usuarioId", id.toString()), admin)
                .andExpect(jsonPath("$.content[0].acao").value("USUARIO_CRIADO"));
    }

    @Test
    @DisplayName("UC01 6a/6b: CNPJ obrigatório para estabelecimento e duplicidade retorna 409")
    void criarInvalidoEDuplicado() throws Exception {
        perform(post("/admin/usuarios").content(PADARIA.replace("\"11.444.777/0001-61\"", "null")), admin)
                .andExpect(status().isBadRequest());
        perform(post("/admin/usuarios").content(PADARIA.replace("padaria@", "ong@")), admin)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("UC01 3a: busca por perfil, situação de análise e nome parcial")
    void listarComFiltros() throws Exception {
        perform(post("/admin/usuarios").content(PADARIA), admin).andExpect(status().isCreated());

        perform(get("/admin/usuarios").param("perfil", "INSTITUICAO").param("statusAnalise", "PENDENTE"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ongId))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        perform(get("/admin/usuarios").param("nome", "padaria"), admin)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Padaria Central"));

        perform(get("/admin/usuarios/" + ongId), admin)
                .andExpect(jsonPath("$.score").value(100));
    }

    @Test
    @DisplayName("UC01 3b: atualização grava no log de auditoria os campos alterados")
    void atualizar() throws Exception {
        String body = """
                {"nome":"ONG Nova","email":"ong@teste.com","telefone":"11999999999","endereco":"Rua da Consolação, 930"}""";
        perform(put("/admin/usuarios/" + ongId).content(body), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("ONG Nova"));

        perform(get("/admin/auditoria").param("usuarioId", ongId.toString()), admin)
                .andExpect(jsonPath("$.content[0].acao").value("USUARIO_ATUALIZADO"))
                .andExpect(jsonPath("$.content[0].detalhes").value("nome: 'ONG Teste' -> 'ONG Nova'"));
    }

    @Test
    @DisplayName("UC01 3c: inativação revoga o acesso na hora e o admin não pode inativar a si mesmo")
    void inativar() throws Exception {
        String tokenOng = login("ong@teste.com", "senha12345");

        perform(delete("/admin/usuarios/" + ongId), admin).andExpect(status().isNoContent());

        perform(get("/auth/me"), tokenOng).andExpect(status().isUnauthorized());
        postJson("/auth/login", "{\"email\":\"ong@teste.com\",\"senha\":\"senha12345\"}").andExpect(status().isUnauthorized());
        perform(get("/admin/usuarios/" + ongId), admin).andExpect(jsonPath("$.ativo").value(false));

        Integer adminId = json(perform(get("/auth/me"), admin), "$.id");
        perform(delete("/admin/usuarios/" + adminId), admin).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RF06: admin aprova ou rejeita (com motivo) apenas instituições")
    void analisarInstituicao() throws Exception {
        perform(post("/admin/usuarios/" + ongId + "/aprovar"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAnalise").value("APROVADA"));

        perform(post("/admin/usuarios/" + ongId + "/rejeitar").content("{}"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.motivo").exists());
        perform(post("/admin/usuarios/" + ongId + "/rejeitar").content("{\"motivo\":\"CNPJ inativo na Receita\"}"), admin)
                .andExpect(jsonPath("$.statusAnalise").value("REJEITADA"));

        perform(get("/admin/auditoria").param("usuarioId", ongId.toString()), admin)
                .andExpect(jsonPath("$.content[0].acao").value("INSTITUICAO_REJEITADA"))
                .andExpect(jsonPath("$.content[0].detalhes").value("CNPJ inativo na Receita"));

        Integer padariaId = json(perform(post("/admin/usuarios").content(PADARIA), admin), "$.usuario.id");
        perform(post("/admin/usuarios/" + padariaId + "/aprovar"), admin).andExpect(status().isBadRequest());
    }
}
