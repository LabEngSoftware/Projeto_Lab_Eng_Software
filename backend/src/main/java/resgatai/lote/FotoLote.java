package resgatai.lote;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Foto do lote, em tabela separada para a busca não carregar os bytes.
 * ponytail: guardada no banco; mover para o S3 se o volume de fotos crescer.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FotoLote {

    @Id
    private Long loteId;

    private String tipo;
    private byte[] conteudo;
}
