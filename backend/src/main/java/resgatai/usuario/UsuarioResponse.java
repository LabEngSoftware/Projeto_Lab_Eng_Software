package resgatai.usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Usuario.Perfil perfil,
        boolean ativo,
        Instituicao.StatusAnalise statusAnalise) {

    public static UsuarioResponse from(Usuario u) {
        var status = u instanceof Instituicao i ? i.getStatusAnalise() : null;
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(), status);
    }
}
