package resgatai.reserva;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import resgatai.lote.Lote;
import resgatai.usuario.Instituicao;

import java.security.SecureRandom;
import java.time.Instant;

/** Reserva de um lote por uma instituição (UC04), com o PIN usado na validação da retirada. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reserva {

    public enum Status { PENDENTE }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Lote lote;

    @ManyToOne(optional = false)
    private Instituicao instituicao;

    private String pin;
    private Instant prazoRetirada;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDENTE;

    private Instant criadoEm = Instant.now();

    /** UC04 passo 8: gera o PIN e define o prazo limite pela janela de retirada informada pelo doador. */
    public Reserva(Lote lote, Instituicao instituicao) {
        this.lote = lote;
        this.instituicao = instituicao;
        this.pin = "%04d".formatted(RANDOM.nextInt(10_000));
        this.prazoRetirada = lote.getRetiradaFim();
    }

    /** Conteúdo do QR Code, desenhado pelo front; leva a reserva e o PIN para o doador validar com uma leitura. */
    public String getQrCode() {
        return "RESGATAI-" + id + "-" + pin;
    }
}
