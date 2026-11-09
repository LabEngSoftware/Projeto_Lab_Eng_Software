package resgatai.lote;

import resgatai.geo.Coordenadas;

import java.math.BigDecimal;
import java.time.Instant;

public record LoteResponse(
        Long id,
        Long estabelecimentoId,
        String estabelecimentoNome,
        String nome,
        Lote.Categoria categoria,
        BigDecimal quantidade,
        Lote.Unidade unidade,
        BigDecimal preco,
        Instant validade,
        Instant retiradaInicio,
        Instant retiradaFim,
        String observacoes,
        String enderecoRetirada,
        Lote.Status status,
        Lote.Urgencia urgencia,
        Double distanciaKm,
        String fotoUrl,
        Instant criadoEm) {

    /** distanciaKm é medida a partir de origem, quando informada. */
    static LoteResponse from(Lote l, Coordenadas origem) {
        Double distancia = origem == null ? null : Math.round(origem.distanciaKm(l.getCoordenadas()) * 10) / 10.0;
        return new LoteResponse(l.getId(), l.getEstabelecimento().getId(), l.getEstabelecimento().getNome(),
                l.getNome(), l.getCategoria(), l.getQuantidade(), l.getUnidade(), l.getPreco(), l.getValidade(),
                l.getRetiradaInicio(), l.getRetiradaFim(), l.getObservacoes(), l.getEnderecoRetirada(),
                l.getStatus(), l.getUrgencia(), distancia, l.isComFoto() ? "/lotes/" + l.getId() + "/foto" : null,
                l.getCriadoEm());
    }
}
