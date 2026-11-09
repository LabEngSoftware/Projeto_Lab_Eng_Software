package resgatai.usuario;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN")
public class Administrador extends Usuario {

    @Override
    public Perfil getPerfil() { return Perfil.ADMIN; }
}
