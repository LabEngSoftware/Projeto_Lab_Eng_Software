package resgatai.usuario;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Critérios de busca de usuários (UC01 fluxo 3a). Campos nulos são ignorados. */
public record UsuarioFiltro(
        String nome,
        String documento,
        Usuario.Perfil perfil,
        Boolean ativo,
        Instituicao.StatusAnalise statusAnalise) {

    Specification<Usuario> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (nome != null && !nome.isBlank()) {
                p.add(cb.like(cb.lower(root.get("nome")), "%" + nome.trim().toLowerCase() + "%"));
            }
            if (documento != null && !documento.isBlank()) {
                p.add(cb.equal(root.get("documento"), UsuarioService.normalizarDocumento(documento)));
            }
            if (perfil != null) {
                p.add(cb.equal(root.type(), switch (perfil) {
                    case ADMIN -> Administrador.class;
                    case ESTABELECIMENTO -> Estabelecimento.class;
                    case INSTITUICAO -> Instituicao.class;
                }));
            }
            if (ativo != null) {
                p.add(cb.equal(root.get("ativo"), ativo));
            }
            if (statusAnalise != null) {
                p.add(cb.equal(cb.treat(root, Instituicao.class).get("statusAnalise"), statusAnalise));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
