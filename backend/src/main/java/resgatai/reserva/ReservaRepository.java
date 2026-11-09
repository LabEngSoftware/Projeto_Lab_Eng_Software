package resgatai.reserva;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Page<Reserva> findByInstituicaoId(Long instituicaoId, Pageable pageable);

    Page<Reserva> findByLoteEstabelecimentoId(Long estabelecimentoId, Pageable pageable);

    @Query("""
            select count(r) > 0 from Reserva r
            where r.status = resgatai.reserva.Reserva.Status.PENDENTE
              and (r.instituicao.id = :usuarioId or r.lote.estabelecimento.id = :usuarioId)""")
    boolean emAndamento(Long usuarioId);
}
