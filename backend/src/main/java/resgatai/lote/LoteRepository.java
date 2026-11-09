package resgatai.lote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoteRepository extends JpaRepository<Lote, Long>, JpaSpecificationExecutor<Lote> {

    Page<Lote> findByEstabelecimentoId(Long estabelecimentoId, Pageable pageable);
}
