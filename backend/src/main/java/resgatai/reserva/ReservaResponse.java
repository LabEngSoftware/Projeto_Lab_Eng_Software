package resgatai.reserva;

import resgatai.lote.LoteResponse;
import resgatai.pagamento.Pagamento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * pin e qrCode só vão para a instituição que reservou; o doador recebe o código dela na retirada.
 * Após a validação, os campos da retirada formam o comprovante (RF05, UC06 passo 12).
 */
public record ReservaResponse(
        Long id,
        Long instituicaoId,
        String instituicaoNome,
        String pin,
        String qrCode,
        Instant prazoRetirada,
        Reserva.Status status,
        Instant criadoEm,
        Instant validadoEm,
        BigDecimal quantidadeEntregue,
        boolean atrasada,
        String motivoCancelamento,
        Pagamento pagamento,
        LoteResponse lote) {

    static ReservaResponse from(Reserva r, boolean comCodigo) {
        return new ReservaResponse(r.getId(), r.getInstituicao().getId(), r.getInstituicao().getNome(),
                comCodigo ? r.getPin() : null, comCodigo ? r.getQrCode() : null,
                r.getPrazoRetirada(), r.getStatus(), r.getCriadoEm(),
                r.getValidadoEm(), r.getQuantidadeEntregue(), r.isAtrasada(), r.getMotivoCancelamento(), r.getPagamento(), LoteResponse.from(r.getLote(), null));
    }
}
