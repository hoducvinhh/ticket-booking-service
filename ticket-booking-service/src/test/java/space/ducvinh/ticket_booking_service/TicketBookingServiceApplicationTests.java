package space.ducvinh.ticket_booking_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class TicketBookingServiceApplicationTests extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void contextLoadsAndPublicEndpointsRespond() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("UP"));

        HttpResponse<String> events = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/events")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, events.statusCode());
        assertTrue(events.body().contains("\"success\":true"));
    }
}
