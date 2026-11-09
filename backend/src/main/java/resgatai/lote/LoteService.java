package resgatai.lote;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import resgatai.auditoria.Auditoria;
import resgatai.auditoria.AuditoriaRepository;
import resgatai.geo.Coordenadas;
import resgatai.geo.Geocodificador;
import resgatai.usuario.Estabelecimento;
import resgatai.usuario.UsuarioService;

import java.util.Set;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class LoteService {

    private static final Set<String> TIPOS_FOTO = Set.of("image/jpeg", "image/png", "image/webp");

    private final LoteRepository lotes;
    private final FotoLoteRepository fotos;
    private final UsuarioService usuarioService;
    private final AuditoriaRepository auditoria;
    private final Geocodificador geocodificador;

    /** UC02 fluxo principal: publica o lote como disponível no endereço do doador ou em um endereço alternativo (7a). */
    @Transactional
    public LoteResponse criar(LoteController.CriarLoteRequest req, Long doadorId) {
        if (!req.retiradaFim().isAfter(req.retiradaInicio())) {
            throw new ResponseStatusException(BAD_REQUEST, "O fim da janela de retirada deve ser posterior ao início");
        }
        if (req.retiradaFim().isAfter(req.validade())) { // UC02 exceção 7a
            throw new ResponseStatusException(BAD_REQUEST, "A janela de retirada não pode terminar depois da validade do lote");
        }

        var doador = (Estabelecimento) usuarioService.buscar(doadorId); // SecurityConfig garante o perfil
        boolean alternativo = req.enderecoRetirada() != null && !req.enderecoRetirada().isBlank();
        String endereco = alternativo ? req.enderecoRetirada().trim() : doador.getEndereco();
        Coordenadas coordenadas = alternativo || doador.getCoordenadas() == null
                ? geocodificador.geocodificar(endereco)
                : doador.getCoordenadas();

        Lote l = new Lote();
        l.setEstabelecimento(doador);
        l.setNome(req.nome().trim());
        l.setCategoria(req.categoria());
        l.setQuantidade(req.quantidade());
        l.setUnidade(req.unidade());
        l.setPreco(req.preco());
        l.setValidade(req.validade());
        l.setRetiradaInicio(req.retiradaInicio());
        l.setRetiradaFim(req.retiradaFim());
        l.setObservacoes(req.observacoes());
        l.setEnderecoRetirada(endereco);
        l.setCoordenadas(coordenadas);
        lotes.save(l);

        auditoria.save(new Auditoria(doadorId, Auditoria.Acao.LOTE_CRIADO, "LOTE", l.getId(), l.getNome()));
        // TODO B7: incluir o CU03, notificando as instituições no raio do lote (UC02 passo 11)
        return LoteResponse.from(l, null);
    }

    /** RF03: busca de lotes disponíveis, com distância a partir do ponto informado ou do endereço do usuário. */
    public Page<LoteResponse> buscar(LoteFiltro filtro, Long usuarioId, Pageable pageable) {
        Coordenadas origem = filtro.latitude() != null && filtro.longitude() != null
                ? new Coordenadas(filtro.latitude(), filtro.longitude())
                : usuarioService.buscar(usuarioId).getCoordenadas();
        return lotes.findAll(filtro.toSpecification(origem), pageable).map(l -> LoteResponse.from(l, origem));
    }

    public LoteResponse consultar(Long id, Long usuarioId) {
        return LoteResponse.from(buscarLote(id), usuarioService.buscar(usuarioId).getCoordenadas());
    }

    /** Lotes do próprio doador em qualquer status, base para repetir um lote anterior (UC02 fluxo 3a). */
    public Page<LoteResponse> meus(Long doadorId, Pageable pageable) {
        return lotes.findByEstabelecimentoId(doadorId, pageable).map(l -> LoteResponse.from(l, null));
    }

    @Transactional
    public void salvarFoto(Long id, Long doadorId, String tipo, byte[] conteudo) {
        Lote l = buscarLote(id);
        if (!l.getEstabelecimento().getId().equals(doadorId)) {
            throw new ResponseStatusException(FORBIDDEN, "O lote pertence a outro estabelecimento");
        }
        if (tipo == null || !TIPOS_FOTO.contains(tipo)) {
            throw new ResponseStatusException(BAD_REQUEST, "A foto deve ser JPEG, PNG ou WebP");
        }
        fotos.save(new FotoLote(id, tipo, conteudo));
        l.setComFoto(true);
    }

    public FotoLote foto(Long id) {
        return fotos.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lote sem foto"));
    }

    private Lote buscarLote(Long id) {
        return lotes.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lote não encontrado"));
    }
}
