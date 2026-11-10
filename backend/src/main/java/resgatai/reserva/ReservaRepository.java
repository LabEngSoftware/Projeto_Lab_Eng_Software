package resgatai.reserva;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Page<Reserva> findByInstituicaoId(Long instituicaoId, Pageable pageable);

    Page<Reserva> findByLoteEstabelecimentoId(Long estabelecimentoId, Pageable pageable);

    @Query("""
            select count(r) > 0 from Reserva r
            where r.status = resgatai.reserva.Reserva.Status.PENDENTE
              and (r.instituicao.id = :usuarioId or r.lote.estabelecimento.id = :usuarioId)""")
    boolean emAndamento(Long usuarioId);

    /** SELECT ... FOR UPDATE: validações simultâneas do mesmo código esperam a primeira terminar (UC06). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reserva> findComBloqueioById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reserva> findComBloqueioByLoteEstabelecimentoIdAndPinOrderByCriadoEmDesc(Long estabelecimentoId, String pin);

    /** Retiradas concluídas por unidade; usuarioId nulo soma a plataforma inteira. */
    @Query("""
            select new resgatai.reserva.Impacto$PorUnidade(r.lote.unidade, count(r), sum(r.quantidadeEntregue))
            from Reserva r
            where r.status = resgatai.reserva.Reserva.Status.COLETADA
              and (:usuarioId is null or r.instituicao.id = :usuarioId or r.lote.estabelecimento.id = :usuarioId)
            group by r.lote.unidade""")
    List<Impacto.PorUnidade> impacto(Long usuarioId);

    /** Travadas como na validação: a expiração espera uma retirada em andamento terminar, e vice-versa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reserva> findByStatusAndPrazoRetiradaBefore(Reserva.Status status, Instant limite);
}
