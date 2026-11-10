package resgatai.reserva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.ApiTest;
import resgatai.auditoria.AuditoriaRepository;
import resgatai.lote.Lote;
import resgatai.lote.LoteRepository;
import resgatai.usuario.Estabelecimento;
import resgatai.usuario.Instituicao;
import resgatai.usuario.Usuario;
import resgatai.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
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
    @Autowired AuditoriaRepository auditoria;
    @Autowired Expiracao expiracao;
    @Autowired JdbcTemplate jdbc;

    final List<Usuario> criados = new ArrayList<>();

    @AfterEach
    void limpar() {
        auditoria.deleteAll();
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

        List<Integer> status = disputar(ongs.size(), i -> reservaService.reservar(lote.getId(), ongs.get(i)));
        assertThat(status).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
        assertThat(reservas.count()).isEqualTo(1);
        assertThat(lotes.findById(lote.getId()).orElseThrow().getStatus()).isEqualTo(Lote.Status.RESERVADO_PENDENTE);
    }

    @Test
    @DisplayName("UC06: validações simultâneas do mesmo código registram uma retirada e pontuam uma vez")
    void validacoesSimultaneas() throws Exception {
        var doador = salvar(new Estabelecimento(), "padaria");
        var ong = new Instituicao();
        ong.setStatusAnalise(Instituicao.StatusAnalise.APROVADA);
        salvar(ong, "ong");
        String qrCode = reservaService.reservar(lotes.save(lote(doador)).getId(), ong.getId()).qrCode();

        List<Integer> status = disputar(INSTITUICOES, i -> reservaService.validar(qrCode, null, false, doador.getId()));
        assertThat(status).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
        assertThat(usuarios.findById(doador.getId()).orElseThrow().getScore()).isEqualTo(100 + ReservaService.PONTOS_RETIRADA);
        assertThat(reservaService.impacto(null).plataforma().retiradas()).isEqualTo(1);
    }

    @RepeatedTest(20) // a janela da disputa é estreita; repetir aumenta a chance de as duas transações se cruzarem
    @DisplayName("Timeout x UC06: validação atrasada e expiração simultâneas terminam em um único desfecho")
    void expiracaoDuranteValidacao() throws Exception {
        var doador = salvar(new Estabelecimento(), "padaria");
        var ong = new Instituicao();
        ong.setStatusAnalise(Instituicao.StatusAnalise.APROVADA);
        salvar(ong, "ong");
        Lote lote = lotes.save(lote(doador));
        String qrCode = reservaService.reservar(lote.getId(), ong.getId()).qrCode();
        jdbc.update("update reserva set prazo_retirada = ?", Timestamp.from(Instant.now().minus(Expiracao.TOLERANCIA).minusSeconds(60)));

        disputar(2, i -> {
            if (i == 0) reservaService.validar(qrCode, null, true, doador.getId());
            else expiracao.expirar();
        });

        // coletada sem penalidade (atrasada não pontua) ou expirada com penalidade, nunca as duas coisas
        var reserva = reservas.findAll().getFirst();
        int score = usuarios.findById(ong.getId()).orElseThrow().getScore();
        var statusLote = lotes.findById(lote.getId()).orElseThrow().getStatus();
        if (reserva.getStatus() == Reserva.Status.COLETADA) {
            assertThat(score).isEqualTo(100);
            assertThat(statusLote).isEqualTo(Lote.Status.COLETADO);
        } else {
            assertThat(reserva.getStatus()).isEqualTo(Reserva.Status.EXPIRADA);
            assertThat(score).isEqualTo(100 - Expiracao.PENALIDADE_NO_SHOW);
            assertThat(statusLote).isEqualTo(Lote.Status.DISPONIVEL);
        }
    }

    /** Dispara as chamadas no mesmo instante, cada uma em sua thread; sucesso vira 200, recusa vira o status HTTP. */
    private static List<Integer> disputar(int threads, IntConsumer chamada) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int n = i;
                resultados.add(pool.submit(() -> {
                    largada.await();
                    try {
                        chamada.accept(n);
                        return 200;
                    } catch (ResponseStatusException e) {
                        return e.getStatusCode().value();
                    }
                }));
            }
            largada.countDown();
        }
        List<Integer> status = new ArrayList<>();
        for (Future<Integer> r : resultados) status.add(r.get());
        return status;
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
