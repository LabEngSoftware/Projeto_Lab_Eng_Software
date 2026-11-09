package resgatai.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Consumo da API do Nominatim com o servidor HTTP simulado. */
class GeocodificadorTest {

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer nominatim = MockRestServiceServer.bindTo(builder).build();
    Geocodificador geocodificador = new Geocodificador(builder);

    @Test
    @DisplayName("Converte o primeiro resultado do Nominatim em coordenadas")
    void encontraEndereco() {
        nominatim.expect(requestTo(startsWith("https://nominatim.openstreetmap.org/search?q=")))
                .andExpect(queryParam("countrycodes", "br"))
                .andExpect(header(HttpHeaders.USER_AGENT, startsWith("ResgatAI")))
                .andRespond(withSuccess("""
                        [{"place_id":1,"lat":"-23.5475","lon":"-46.6520","display_name":"Rua da Consolação, 930"}]""",
                        APPLICATION_JSON));

        assertThat(geocodificador.geocodificar("Rua da Consolação, 930, São Paulo"))
                .isEqualTo(new Coordenadas(-23.5475, -46.6520));
    }

    @Test
    @DisplayName("Endereço sem resultado retorna 400 e falha do serviço retorna 503")
    void falhas() {
        nominatim.expect(requestTo(startsWith("https://nominatim"))).andRespond(withSuccess("[]", APPLICATION_JSON));
        nominatim.expect(requestTo(startsWith("https://nominatim"))).andRespond(withServerError());

        assertThatThrownBy(() -> geocodificador.geocodificar("xyz"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> geocodificador.geocodificar("xyz"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }
}
