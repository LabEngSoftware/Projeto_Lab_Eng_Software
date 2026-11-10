package resgatai.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByTokenSenhaHash(String tokenSenhaHash);

    boolean existsByEmail(String email);

    boolean existsByDocumento(String documento);

    @Query("select i from Instituicao i where i.ativo = true and i.statusAnalise = resgatai.usuario.Instituicao.StatusAnalise.APROVADA")
    List<Instituicao> instituicoesAprovadas();
}
