package resgatai.usuario;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** Receptor: ONG, igreja, abrigo. Só reserva lotes depois de aprovada pelo administrador (RF06). */
@Entity
@DiscriminatorValue("INSTITUICAO")
@Getter
@Setter
public class Instituicao extends Usuario {

    public enum StatusAnalise { PENDENTE, APROVADA, REJEITADA }

    @Enumerated(EnumType.STRING)
    private StatusAnalise statusAnalise = StatusAnalise.PENDENTE;

    @Override
    public Perfil getPerfil() { return Perfil.INSTITUICAO; }
}
