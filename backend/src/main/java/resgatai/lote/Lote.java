package resgatai.lote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import resgatai.geo.Coordenadas;
import resgatai.usuario.Estabelecimento;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Lote de alimentos perecíveis publicado por um estabelecimento (RF02 / UC02). */
@Entity
@Getter
@Setter
public class Lote {

    public enum Categoria { PADARIA, HORTIFRUTI, LATICINIOS, CARNES, REFEICOES_PRONTAS, BEBIDAS, MERCEARIA, OUTROS }

    public enum Unidade { UNIDADE, KG, LITRO, PACOTE, CAIXA }

    public enum Status { DISPONIVEL, RESERVADO_PENDENTE, COLETADO, CANCELADO, EXPIRADO }

    public enum Urgencia { ALTA, MEDIA, BAIXA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Estabelecimento estabelecimento;

    private String nome;

    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    private Unidade unidade;

    private BigDecimal preco;
    private Instant validade;
    private Instant retiradaInicio;
    private Instant retiradaFim;
    private String observacoes;

    /** Endereço do estabelecimento ou o endereço alternativo informado só para este lote (UC02 fluxo 7a). */
    private String enderecoRetirada;

    @Embedded
    private Coordenadas coordenadas;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DISPONIVEL;

    private boolean comFoto;
    private Instant criadoEm = Instant.now();

    /** UC02 passo 6: indicador de urgência pelo prazo restante até o vencimento. */
    public Urgencia getUrgencia() {
        Duration restante = Duration.between(Instant.now(), validade);
        if (restante.compareTo(Duration.ofHours(6)) < 0) return Urgencia.ALTA;
        if (restante.compareTo(Duration.ofHours(24)) < 0) return Urgencia.MEDIA;
        return Urgencia.BAIXA;
    }
}
