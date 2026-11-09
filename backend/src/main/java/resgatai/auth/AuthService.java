package resgatai.auth;

import resgatai.usuario.Estabelecimento;
import resgatai.usuario.Instituicao;
import resgatai.usuario.Usuario;
import resgatai.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiracao}")
    private Duration expiracao;

    @Transactional
    public Usuario registrar(AuthController.RegistroRequest req) {
        String email = req.email().trim().toLowerCase();
        String documento = req.documento().replaceAll("\\D", "");
        if (usuarios.existsByEmail(email) || usuarios.existsByDocumento(documento)) {
            throw new ResponseStatusException(CONFLICT, "Já existe um usuário com este e-mail ou CNPJ");
        }

        Usuario u = switch (req.perfil()) {
            case ESTABELECIMENTO -> new Estabelecimento();
            case INSTITUICAO -> new Instituicao();
            case ADMIN -> throw new ResponseStatusException(BAD_REQUEST, "Perfil não permitido no auto cadastro");
        };
        u.setNome(req.nome().trim());
        u.setDocumento(documento);
        u.setEmail(email);
        u.setSenha(encoder.encode(req.senha()));
        u.setTelefone(req.telefone());
        u.setEndereco(req.endereco());
        u.setAceiteLgpdEm(Instant.now());
        return usuarios.save(u);
    }

    public String login(String email, String senha) {
        Usuario u = usuarios.findByEmail(email.trim().toLowerCase())
                .filter(Usuario::isAtivo)
                .filter(x -> encoder.matches(senha, x.getSenha()))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "E-mail ou senha inválidos"));

        Instant agora = Instant.now();
        // O perfil vai na claim "scope" e o Spring o expõe como a authority SCOPE_<PERFIL>.
        // ponytail: token não é revogado ao inativar o usuário; vale até expirar (app.jwt.expiracao).
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(u.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(expiracao))
                .claim("scope", u.getPerfil().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Usuario usuarioAtual(Jwt jwt) {
        return usuarios.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }
}
