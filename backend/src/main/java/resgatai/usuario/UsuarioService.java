package resgatai.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.auditoria.Auditoria;
import resgatai.auditoria.AuditoriaRepository;
import resgatai.geo.Geocodificador;
import resgatai.notificacao.Notificador;
import resgatai.reserva.ReservaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.springframework.http.HttpStatus.*;
import static resgatai.auditoria.Auditoria.Acao.*;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Duration VALIDADE_LINK_SENHA = Duration.ofHours(48);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final AuditoriaRepository auditoria;
    private final PasswordEncoder encoder;
    private final Geocodificador geocodificador;
    private final ReservaRepository reservas;
    private final Notificador notificador;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public record UsuarioCriado(Usuario usuario, String linkPrimeiroAcesso) {}

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    public static String normalizarDocumento(String documento) {
        return documento == null || documento.isBlank() ? null : documento.replaceAll("\\D", "");
    }

    /** Monta um usuário novo (ainda sem senha) após validar duplicidade de e-mail e CNPJ (UC01 fluxo 6b). */
    public Usuario novo(Usuario.Perfil perfil, String nome, String documento, String email,
                        String telefone, String endereco) {
        email = normalizarEmail(email);
        documento = normalizarDocumento(documento);
        if (documento == null && perfil != Usuario.Perfil.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "CNPJ é obrigatório para estabelecimentos e instituições");
        }
        if (usuarios.existsByEmail(email) || (documento != null && usuarios.existsByDocumento(documento))) {
            throw new ResponseStatusException(CONFLICT, "Já existe um usuário com este e-mail ou CNPJ");
        }

        Usuario u = switch (perfil) {
            case ADMIN -> new Administrador();
            case ESTABELECIMENTO -> new Estabelecimento();
            case INSTITUICAO -> new Instituicao();
        };
        u.setNome(nome.trim());
        u.setDocumento(documento);
        u.setEmail(email);
        u.setTelefone(telefone);
        u.setEndereco(endereco);
        if (perfil != Usuario.Perfil.ADMIN) {
            u.setCoordenadas(geocodificador.geocodificar(endereco)); // valida o endereço de retirada/atuação
        }
        return u;
    }

    public Page<Usuario> listar(UsuarioFiltro filtro, Pageable pageable) {
        return usuarios.findAll(filtro.toSpecification(), pageable);
    }

    public Usuario buscar(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuário não encontrado"));
    }

    /** Cadastro feito pelo administrador (UC01 fluxo principal): o usuário define a senha pelo link de primeiro acesso. */
    @Transactional
    public UsuarioCriado criar(Usuario.Perfil perfil, String nome, String documento, String email,
                               String telefone, String endereco, Long autorId) {
        Usuario u = novo(perfil, nome, documento, email, telefone, endereco);
        u.setSenha(encoder.encode(gerarToken())); // senha aleatória, inutilizável até o primeiro acesso

        String token = gerarToken();
        u.setTokenSenhaHash(sha256(token));
        u.setTokenSenhaExpiraEm(Instant.now().plus(VALIDADE_LINK_SENHA));
        usuarios.save(u);

        auditoria.save(new Auditoria(autorId, USUARIO_CRIADO, "USUARIO", u.getId(), "perfil: " + perfil));
        // UC01 passo 8: o link vai por e-mail; sem SMTP (ou se o envio falhar) volta na resposta para o admin repassar
        String link = frontendUrl + "/definir-senha?token=" + token;
        boolean enviado = notificador.enviarEmail(u, "ResgatAI: defina sua senha",
                "Olá, %s!\n\nSeu cadastro no ResgatAI foi criado. Defina sua senha em até 48 horas:\n%s".formatted(u.getNome(), link));
        return new UsuarioCriado(u, enviado ? null : link);
    }

    @Transactional
    public void definirSenha(String token, String senha) {
        Usuario u = usuarios.findByTokenSenhaHash(sha256(token))
                .filter(x -> x.getTokenSenhaExpiraEm().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Link inválido ou expirado"));
        u.setSenha(encoder.encode(senha));
        u.setAceiteLgpdEm(Instant.now()); // o aceite é exigido no DefinirSenhaRequest
        u.setTokenSenhaHash(null);
        u.setTokenSenhaExpiraEm(null);
    }

    /** UC01 fluxo 3b: grava a alteração e registra no log os campos modificados. */
    @Transactional
    public Usuario atualizar(Long id, String nome, String email, String telefone, String endereco, Long autorId) {
        Usuario u = buscar(id);
        email = normalizarEmail(email);
        if (!email.equals(u.getEmail()) && usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(CONFLICT, "Já existe um usuário com este e-mail");
        }

        if (!(u instanceof Administrador) && !Objects.equals(u.getEndereco(), endereco)) {
            u.setCoordenadas(geocodificador.geocodificar(endereco));
        }

        List<String> mudancas = new ArrayList<>();
        alterar("nome", u.getNome(), nome.trim(), u::setNome, mudancas);
        alterar("email", u.getEmail(), email, u::setEmail, mudancas);
        alterar("telefone", u.getTelefone(), telefone, u::setTelefone, mudancas);
        alterar("endereco", u.getEndereco(), endereco, u::setEndereco, mudancas);

        if (!mudancas.isEmpty()) {
            auditoria.save(new Auditoria(autorId, USUARIO_ATUALIZADO, "USUARIO", id, String.join("; ", mudancas)));
        }
        return u;
    }

    /** UC01 fluxo 3c: inativa (não apaga) para preservar o histórico; o token JWT deixa de valer na hora. */
    @Transactional
    public void inativar(Long id, Long autorId) {
        if (id.equals(autorId)) {
            throw new ResponseStatusException(BAD_REQUEST, "O administrador não pode inativar a própria conta");
        }
        Usuario u = buscar(id);
        // UC01 3c passo 2: lotes de doador inativo já saem da busca (LoteFiltro); reserva em andamento bloqueia
        if (reservas.emAndamento(id)) {
            throw new ResponseStatusException(CONFLICT, "O usuário possui reservas em andamento; aguarde a retirada ou a expiração");
        }
        if (u.isAtivo()) {
            u.setAtivo(false);
            auditoria.save(new Auditoria(autorId, USUARIO_INATIVADO, "USUARIO", id, null));
        }
    }

    /** RF06: aprovação ou rejeição do cadastro de uma instituição. */
    @Transactional
    public Usuario analisarInstituicao(Long id, Instituicao.StatusAnalise status, String motivo, Long autorId) {
        if (!(buscar(id) instanceof Instituicao instituicao)) {
            throw new ResponseStatusException(BAD_REQUEST, "Apenas instituições passam por análise de cadastro");
        }
        instituicao.setStatusAnalise(status);
        var acao = status == Instituicao.StatusAnalise.APROVADA ? INSTITUICAO_APROVADA : INSTITUICAO_REJEITADA;
        auditoria.save(new Auditoria(autorId, acao, "USUARIO", id, motivo));
        return instituicao;
    }

    private static void alterar(String campo, String atual, String novo, Consumer<String> setter, List<String> mudancas) {
        if (!Objects.equals(atual, novo)) {
            setter.accept(novo);
            mudancas.add("%s: '%s' -> '%s'".formatted(campo, atual, novo));
        }
    }

    private static String gerarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 é obrigatório em toda JVM
        }
    }
}
