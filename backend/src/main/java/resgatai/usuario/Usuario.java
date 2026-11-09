package resgatai.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "perfil")
@Getter
@Setter
public abstract class Usuario {

    public enum Perfil { ADMIN, ESTABELECIMENTO, INSTITUICAO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String documento;
    private String email;
    private String senha;
    private String telefone;
    private String endereco;
    private boolean ativo = true;
    private Instant aceiteLgpdEm;
    private Instant criadoEm = Instant.now();

    public abstract Perfil getPerfil();
}
