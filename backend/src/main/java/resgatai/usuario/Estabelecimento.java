package resgatai.usuario;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Doador: mercado, padaria, hortifrúti. */
@Entity
@DiscriminatorValue("ESTABELECIMENTO")
public class Estabelecimento extends Usuario {

    @Override
    public Perfil getPerfil() { return Perfil.ESTABELECIMENTO; }
}
