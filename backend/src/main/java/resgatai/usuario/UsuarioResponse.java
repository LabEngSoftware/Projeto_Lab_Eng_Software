package resgatai.usuario;

import java.time.Instant;

public record UsuarioResponse(
        Long id,
        Usuario.Perfil perfil,
        String nome,
        String documento,
        String email,
        String telefone,
        String endereco,
        boolean ativo,
        int score,
        Instituicao.StatusAnalise statusAnalise,
        Instant criadoEm) {

    public static UsuarioResponse from(Usuario u) {
        var status = u instanceof Instituicao i ? i.getStatusAnalise() : null;
        return new UsuarioResponse(u.getId(), u.getPerfil(), u.getNome(), u.getDocumento(), u.getEmail(),
                u.getTelefone(), u.getEndereco(), u.isAtivo(), u.getScore(), status, u.getCriadoEm());
    }
}
