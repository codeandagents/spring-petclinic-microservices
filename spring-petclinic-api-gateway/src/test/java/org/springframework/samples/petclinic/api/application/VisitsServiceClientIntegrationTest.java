package org.springframework.samples.petclinic.api.application;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisitsServiceClientIntegrationTest {

    private static final Integer PET_ID = 1;

    private VisitsServiceClient visitsServiceClient;

    private MockWebServer server;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        visitsServiceClient = new VisitsServiceClient(WebClient.builder());
        visitsServiceClient.setHostname(server.url("/").toString());
    }

    @AfterEach
    void shutdown() throws IOException {
        this.server.close();
    }

    @Test
    void getVisitsForPets_withAvailableVisitsService() {
        prepareResponse();

        Mono<Visits> visits = visitsServiceClient.getVisitsForPets(Collections.singletonList(1));

        assertVisitDescriptionEquals(visits.block(), PET_ID,"test visit");
    }

    @Test
    void getUpcomingVisits_sendsDaysWhenGiven() throws InterruptedException {
        prepareResponse();

        Visits visits = visitsServiceClient.getUpcomingVisits(3).block();

        assertVisitDescriptionEquals(visits, PET_ID, "test visit");
        assertEquals("/visits/upcoming?days=3", server.takeRequest().getPath());
    }

    @Test
    void getUpcomingVisits_leavesDaysOutWhenNotGiven() throws InterruptedException {
        prepareResponse();

        visitsServiceClient.getUpcomingVisits(null).block();

        assertEquals("/visits/upcoming", server.takeRequest().getPath());
    }

    @Test
    void getUpcomingVisits_mapsABadRequestToABadRequest() {
        server.enqueue(new MockResponse.Builder().code(400).build());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> visitsServiceClient.getUpcomingVisits(0).block());

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    private void assertVisitDescriptionEquals(Visits visits, int petId, String description) {
        assertEquals(1, visits.items().size());
        assertNotNull(visits.items().get(0));
        assertEquals(petId, visits.items().get(0).petId());
        assertEquals(description, visits.items().get(0).description());
    }

    private void prepareResponse() {
        MockResponse response = new MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("{\"items\":[{\"id\":5,\"date\":\"2018-11-15\",\"description\":\"test visit\",\"petId\":1}]}")
            .build();
        this.server.enqueue(response);
    }

}
