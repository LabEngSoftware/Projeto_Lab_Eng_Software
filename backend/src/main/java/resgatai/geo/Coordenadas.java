package resgatai.geo;

import jakarta.persistence.Embeddable;

@Embeddable
public record Coordenadas(double latitude, double longitude) {

    public static final double KM_POR_GRAU = 111.32;

    // ponytail: aproximação equiretangular, erro < 1% em raios urbanos (até ~50 km); trocar por PostGIS se precisar de mais.
    public double distanciaKm(Coordenadas outra) {
        double dx = (outra.longitude - longitude) * Math.cos(Math.toRadians(latitude));
        double dy = outra.latitude - latitude;
        return Math.hypot(dx, dy) * KM_POR_GRAU;
    }
}
