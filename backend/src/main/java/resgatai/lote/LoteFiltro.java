package resgatai.lote;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.data.jpa.domain.Specification;
import resgatai.geo.Coordenadas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Critérios de busca de lotes (RF03 / UC04 fluxo 2a). Campos nulos são ignorados.
 * Sem latitude/longitude, o raio é medido a partir do endereço do usuário logado.
 */
public record LoteFiltro(
        String nome,
        Lote.Categoria categoria,
        BigDecimal precoMin,
        BigDecimal precoMax,
        Instant venceAte,
        Double latitude,
        Double longitude,
        @Positive @Max(100) Double raioKm) {

    static final double RAIO_PADRAO_KM = 10;

    Specification<Lote> toSpecification(Coordenadas origem) {
        Instant agora = Instant.now();
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            // RNF02: some da listagem o lote indisponível, de doador inativo ou com a janela de retirada encerrada
            // (a janela termina no máximo na validade, então isso também esconde os vencidos)
            p.add(cb.equal(root.get("status"), Lote.Status.DISPONIVEL));
            p.add(cb.greaterThan(root.get("retiradaFim"), agora));
            p.add(cb.isTrue(root.get("estabelecimento").<Boolean>get("ativo")));

            if (nome != null && !nome.isBlank()) {
                p.add(cb.like(cb.lower(root.get("nome")), "%" + nome.trim().toLowerCase() + "%"));
            }
            if (categoria != null) {
                p.add(cb.equal(root.get("categoria"), categoria));
            }
            if (precoMin != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("preco"), precoMin));
            }
            if (precoMax != null) {
                p.add(cb.lessThanOrEqualTo(root.get("preco"), precoMax));
            }
            if (venceAte != null) {
                p.add(cb.lessThanOrEqualTo(root.get("validade"), venceAte));
            }
            if (origem != null) {
                // mesma conta de Coordenadas.distanciaKm, comparando o quadrado da distância em graus
                var c = root.get("coordenadas");
                var dy = cb.diff(c.<Double>get("latitude"), origem.latitude());
                var dx = cb.prod(cb.diff(c.<Double>get("longitude"), origem.longitude()),
                        Math.cos(Math.toRadians(origem.latitude())));
                double raioGraus = (raioKm != null ? raioKm : RAIO_PADRAO_KM) / Coordenadas.KM_POR_GRAU;
                p.add(cb.le(cb.sum(cb.prod(dx, dx), cb.prod(dy, dy)), raioGraus * raioGraus));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
