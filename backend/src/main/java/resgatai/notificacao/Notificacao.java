package resgatai.notificacao;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import resgatai.usuario.Usuario;

import java.time.Instant;

/** RF07: notificação na caixa de entrada do usuário (lida pelo PWA) e, até ser enviada, fila do e-mail. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Usuario usuario;

    private String titulo;
    private String mensagem;

    /** Caminho no front (ex.: /lotes/12) para abrir o assunto da notificação. */
    private String link;

    @Setter
    private boolean lida;

    private Instant criadoEm = Instant.now();

    @JsonIgnore
    @Setter
    private Instant emailEnviadoEm;

    Notificacao(Usuario usuario, String titulo, String mensagem, String link) {
        this.usuario = usuario;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.link = link;
    }
}
