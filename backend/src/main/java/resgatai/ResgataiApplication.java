package resgatai;

import resgatai.usuario.Administrador;
import resgatai.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class ResgataiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResgataiApplication.class, args);
    }

    /** Garante que exista um administrador; as credenciais vêm de app.admin.* (variáveis de ambiente em produção). */
    @Bean
    CommandLineRunner criarAdmin(UsuarioRepository usuarios, PasswordEncoder encoder,
                                 @Value("${app.admin.email}") String email,
                                 @Value("${app.admin.senha}") String senha) {
        return args -> {
            if (usuarios.existsByEmail(email)) return;
            var admin = new Administrador();
            admin.setNome("Administrador");
            admin.setEmail(email);
            admin.setSenha(encoder.encode(senha));
            usuarios.save(admin);
        };
    }
}
