package resgatai.lote;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Long>, JpaSpecificationExecutor<Lote> {

    Page<Lote> findByEstabelecimentoId(Long estabelecimentoId, Pageable pageable);

    /** SELECT ... FOR UPDATE: segura a linha do lote até o fim da transação (UC04 passo 5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Lote> findComBloqueioById(Long id);

    /** Travados como na reserva: uma reserva em andamento termina antes de o lote expirar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Lote> findByStatusAndRetiradaFimBefore(Lote.Status status, Instant limite);
}
