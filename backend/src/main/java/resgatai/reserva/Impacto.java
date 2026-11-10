package resgatai.reserva;

import resgatai.lote.Lote;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Indicadores de impacto (UC06 passo 11): retiradas concluídas e quantidade resgatada por unidade de medida. */
public record Impacto(long retiradas, Map<Lote.Unidade, BigDecimal> quantidades) {

    public record PorUnidade(Lote.Unidade unidade, long retiradas, BigDecimal quantidade) {}

    static Impacto de(List<PorUnidade> linhas) {
        var quantidades = new EnumMap<Lote.Unidade, BigDecimal>(Lote.Unidade.class);
        linhas.forEach(l -> quantidades.put(l.unidade(), l.quantidade()));
        return new Impacto(linhas.stream().mapToLong(PorUnidade::retiradas).sum(), quantidades);
    }
}
