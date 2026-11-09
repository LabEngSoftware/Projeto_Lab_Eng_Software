package resgatai.reserva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.ApiTest;
import resgatai.lote.Lote;
import resgatai.lote.LoteRepository;
import resgatai.usuario.Estabelecimento;
import resgatai.usuario.Instituicao;
import resgatai.usuario.Usuario;
import resgatai.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC04: várias instituições disputam o mesmo lote no mesmo instante, cada uma em sua thread e transação.
 * Sem a transação de teste (que desfaria tudo), os dados são gravados de verdade e apagados no fim.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservaConcorrenciaTest extends ApiTest {

    static final int INSTITUICOES = 10;

    @Autowired ReservaService reservaService;
    @Autowired ReservaRepository reservas;
    @Autowired LoteRepository lotes;
    @Autowired UsuarioRepository usuarios;

    final List<Usuario> criados = new ArrayList<>();

    @AfterEach
    void limpar() {
        reservas.deleteAll();
        lotes.deleteAll();
        usuarios.deleteAll(criados);
    }

    @Test
    @DisplayName("UC04: entre requisições simultâneas, só uma reserva o lote e as demais recebem 409")
    void reservasSimultaneas() throws Exception {
        Lote lote = lotes.save(lote(salvar(new Estabelecimento(), "padaria")));
        List<Long> ongs = IntStream.range(0, INSTITUICOES).mapToObj(i -> {
            var ong = new Instituicao();
            ong.setStatusAnalise(Instituicao.StatusAnalise.APROVADA);
            return salvar(ong, "ong" + i).getId();
        }).toList();

        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(INSTITUICOES)) {
            for (Long ong : ongs) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    try {
                        reservaService.reservar(lote.getId(), ong);
                        return 201;
                    } catch (ResponseStatusException e) {
                        return e.getStatusCode().value();
                    }
                }));
            }
            largada.countDown();
        }

        List<Integer> status = new ArrayList<>();
        for (Future<Integer> r : resultados) status.add(r.get());
        assertThat(status).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
        assertThat(reservas.count()).isEqualTo(1);
        assertThat(lotes.findById(lote.getId()).orElseThrow().getStatus()).isEqualTo(Lote.Status.RESERVADO_PENDENTE);
    }

    private <T extends Usuario> T salvar(T u, String nome) {
        u.setNome(nome);
        u.setEmail(nome + "@concorrencia.com");
        u.setSenha("x");
        u.setEndereco("Rua da Consolação, 930");
        u.setCoordenadas(MACKENZIE);
        criados.add(usuarios.save(u));
        return u;
    }

    private static Lote lote(Estabelecimento doador) {
        Instant agora = Instant.now();
        var l = new Lote();
        l.setEstabelecimento(doador);
        l.setNome("Pão francês");
        l.setCategoria(Lote.Categoria.PADARIA);
        l.setQuantidade(BigDecimal.TEN);
        l.setUnidade(Lote.Unidade.UNIDADE);
        l.setPreco(BigDecimal.ZERO);
        l.setValidade(agora.plus(Duration.ofHours(3)));
        l.setRetiradaInicio(agora);
        l.setRetiradaFim(agora.plus(Duration.ofHours(3)));
        l.setEnderecoRetirada(doador.getEndereco());
        l.setCoordenadas(doador.getCoordenadas());
        return l;
    }
}
