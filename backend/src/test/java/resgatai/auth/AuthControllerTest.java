package resgatai.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import resgatai.ApiTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ApiTest {

    @Test
    @DisplayName("Instituição se cadastra como PENDENTE, faz login e consulta o próprio perfil")
    void cadastroLoginEPerfil() throws Exception {
        postJson("/auth/registro", ONG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("INSTITUICAO"))
                .andExpect(jsonPath("$.statusAnalise").value("PENDENTE"))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.senha").doesNotExist());

        String token = login("ONG@teste.com", "senha12345");

        perform(get("/auth/me"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ong@teste.com"));
    }

    @Test
    @DisplayName("UC01 6b: e-mail ou CNPJ já cadastrado retorna 409")
    void cadastroDuplicado() throws Exception {
        postJson("/auth/registro", ONG).andExpect(status().isCreated());
        // mesmo CNPJ sem máscara, outro e-mail
        postJson("/auth/registro", ONG.replace("11.222.333/0001-81", "11222333000181").replace("ong@", "outra@"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("UC01 6a: dados inválidos retornam 400 apontando cada campo")
    void cadastroInvalido() throws Exception {
        postJson("/auth/registro", ONG.replace("11.222.333/0001-81", "12.345.678/0001-00").replace("\"aceiteLgpd\":true", "\"aceiteLgpd\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.documento").exists())
                .andExpect(jsonPath("$.erros.aceiteLgpd").exists());

        postJson("/auth/registro", "{\"perfil\":\"INSTITUICAO\",\"email\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.aceiteLgpd").exists());
    }

    @Test
    @DisplayName("Não é possível se auto cadastrar como administrador")
    void cadastroComoAdmin() throws Exception {
        postJson("/auth/registro", ONG.replace("INSTITUICAO", "ADMIN")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Senha errada e requisição sem token retornam 401")
    void naoAutenticado() throws Exception {
        postJson("/auth/registro", ONG).andExpect(status().isCreated());
        postJson("/auth/login", "{\"email\":\"ong@teste.com\",\"senha\":\"errada123\"}").andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        perform(get("/auth/me"), "token-falso").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Administrador padrão é criado na inicialização e consegue logar")
    void adminInicial() throws Exception {
        perform(get("/auth/me"), login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
    }
}
