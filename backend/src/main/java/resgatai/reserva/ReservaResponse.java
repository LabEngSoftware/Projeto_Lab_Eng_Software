package resgatai.reserva;

import resgatai.lote.LoteResponse;

import java.time.Instant;

/** pin e qrCode só vão para a instituição que reservou; o doador recebe o código dela na retirada. */
public record ReservaResponse(
        Long id,
        Long instituicaoId,
        String instituicaoNome,
        String pin,
        String qrCode,
        Instant prazoRetirada,
        Reserva.Status status,
        Instant criadoEm,
        LoteResponse lote) {

    static ReservaResponse from(Reserva r, boolean comCodigo) {
        return new ReservaResponse(r.getId(), r.getInstituicao().getId(), r.getInstituicao().getNome(),
                comCodigo ? r.getPin() : null, comCodigo ? r.getQrCode() : null,
                r.getPrazoRetirada(), r.getStatus(), r.getCriadoEm(), LoteResponse.from(r.getLote(), null));
    }
}
