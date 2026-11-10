package resgatai.auditoria;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Log de auditoria: quem fez o quê, quando e em qual registro (UC01). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auditoria {

    public enum Acao {
        USUARIO_CRIADO, USUARIO_ATUALIZADO, USUARIO_INATIVADO, INSTITUICAO_APROVADA, INSTITUICAO_REJEITADA,
        LOTE_CRIADO, LOTE_EXPIRADO, RETIRADA_VALIDADA, RETIRADA_CANCELADA, RESERVA_EXPIRADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long autorId;

    @Enumerated(EnumType.STRING)
    private Acao acao;

    private String entidade;
    private Long entidadeId;
    private String detalhes;
    private Instant criadoEm = Instant.now();

    public Auditoria(Long autorId, Acao acao, String entidade, Long entidadeId, String detalhes) {
        this.autorId = autorId;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes;
    }
}
