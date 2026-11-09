package resgatai.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Converte endereço em latitude/longitude pela API pública do Nominatim (OpenStreetMap). */
@Component
public class Geocodificador {

    private final RestClient http;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(String lat, String lon) {}

    @Autowired
    public Geocodificador() {
        this(RestClient.builder().requestFactory(comTimeout(Duration.ofSeconds(5))));
    }

    Geocodificador(RestClient.Builder builder) {
        // A política de uso do Nominatim exige um User-Agent que identifique a aplicação
        this.http = builder.baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader(HttpHeaders.USER_AGENT, "ResgatAI/1.0 (projeto academico Mackenzie)")
                .build();
    }

    public Coordenadas geocodificar(String endereco) {
        Resultado[] r;
        try {
            r = http.get()
                    .uri(u -> u.path("/search").queryParam("q", endereco).queryParam("format", "jsonv2")
                            .queryParam("countrycodes", "br").queryParam("limit", 1).build())
                    .retrieve()
                    .body(Resultado[].class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Serviço de geolocalização indisponível, tente novamente", e);
        }
        if (r == null || r.length == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Endereço não encontrado: informe rua, número e cidade");
        }
        return new Coordenadas(Double.parseDouble(r[0].lat()), Double.parseDouble(r[0].lon()));
    }

    private static SimpleClientHttpRequestFactory comTimeout(Duration timeout) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeout);
        f.setReadTimeout(timeout);
        return f;
    }
}
