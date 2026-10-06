package org.springframework.samples.petclinic.api.boundary.web;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.api.application.CustomersServiceClient;
import org.springframework.samples.petclinic.api.application.VisitsServiceClient;
import org.springframework.samples.petclinic.api.dto.OwnerDetails;
import org.springframework.samples.petclinic.api.dto.PetDetails;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@WebFluxTest(controllers = ApiGatewayController.class)
@Import({ReactiveResilience4JAutoConfiguration.class, CircuitBreakerConfiguration.class})
class ApiGatewayControllerTest {

    @MockitoBean
    private CustomersServiceClient customersServiceClient;

    @MockitoBean
    private VisitsServiceClient visitsServiceClient;

    @Autowired
    private WebTestClient client;


    @Test
    void getOwnerDetails_withAvailableVisitsService() {
        PetDetails cat = PetDetails.PetDetailsBuilder.aPetDetails()
            .id(20)
            .name("Garfield")
            .visits(new ArrayList<>())
            .build();
        OwnerDetails owner = OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .pets(List.of(cat))
            .build();
        Mockito
            .when(customersServiceClient.getOwner(1))
            .thenReturn(Mono.just(owner));

        VisitDetails visit = new VisitDetails(300, cat.id(), null, "First visit");
        Visits visits = new Visits(List.of(visit));
        Mockito
            .when(visitsServiceClient.getVisitsForPets(Collections.singletonList(cat.id())))
            .thenReturn(Mono.just(visits));

        client.get()
            .uri("/api/gateway/owners/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.pets[0].name").isEqualTo("Garfield")
            .jsonPath("$.pets[0].visits[0].description").isEqualTo("First visit");
    }

    /**
     * Test Resilience4j fallback method
     */
    @Test
    void getOwnerDetails_withServiceError() {
        PetDetails cat = PetDetails.PetDetailsBuilder.aPetDetails()
            .id(20)
            .name("Garfield")
            .visits(new ArrayList<>())
            .build();
        OwnerDetails owner = OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .pets(List.of(cat))
            .build();
        Mockito
            .when(customersServiceClient.getOwner(1))
            .thenReturn(Mono.just(owner));

        Mockito
            .when(visitsServiceClient.getVisitsForPets(Collections.singletonList(cat.id())))
            .thenReturn(Mono.error(new ConnectException("Simulate error")));

        client.get()
            .uri("/api/gateway/owners/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.pets[0].name").isEqualTo("Garfield")
            .jsonPath("$.pets[0].visits").isEmpty();
    }

    private OwnerDetails ownerWithPet(int ownerId, String first, String last, int petId, String petName) {
        PetDetails pet = PetDetails.PetDetailsBuilder.aPetDetails().id(petId).name(petName).build();
        return OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .id(ownerId).firstName(first).lastName(last).pets(List.of(pet)).build();
    }

    @Test
    void getUpcomingVisits_addsPetAndOwner() {
        Mockito.when(visitsServiceClient.getUpcomingVisits(null)).thenReturn(Mono.just(new Visits(List.of(
            new VisitDetails(1, 20, "2026-10-21", "checkup"),
            new VisitDetails(2, 21, "2026-10-22", "rabies shot")))));
        Mockito.when(customersServiceClient.getOwners()).thenReturn(Flux.just(
            ownerWithPet(5, "Betty", "Davis", 20, "Leo"),
            ownerWithPet(6, "Eduardo", "Rodriquez", 21, "Rosy")));

        client.get()
            .uri("/api/gateway/visits/upcoming")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].id").isEqualTo(1)
            .jsonPath("$.items[0].date").isEqualTo("2026-10-21")
            .jsonPath("$.items[0].description").isEqualTo("checkup")
            .jsonPath("$.items[0].petId").isEqualTo(20)
            .jsonPath("$.items[0].petName").isEqualTo("Leo")
            .jsonPath("$.items[0].ownerId").isEqualTo(5)
            .jsonPath("$.items[0].ownerName").isEqualTo("Betty Davis")
            .jsonPath("$.items[1].ownerName").isEqualTo("Eduardo Rodriquez");
    }

    @Test
    void getUpcomingVisits_passesDaysToVisitsService() {
        Mockito.when(visitsServiceClient.getUpcomingVisits(3)).thenReturn(Mono.just(new Visits(List.of())));
        Mockito.when(customersServiceClient.getOwners()).thenReturn(Flux.empty());

        client.get()
            .uri("/api/gateway/visits/upcoming?days=3")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items").isEmpty();
        Mockito.verify(visitsServiceClient).getUpcomingVisits(3);
    }

    @Test
    void getUpcomingVisits_leavesOutVisitsOfUnknownPets() {
        Mockito.when(visitsServiceClient.getUpcomingVisits(null)).thenReturn(Mono.just(new Visits(List.of(
            new VisitDetails(1, 99, "2026-10-21", "orphan"),
            new VisitDetails(2, 20, "2026-10-21", "checkup")))));
        Mockito.when(customersServiceClient.getOwners())
            .thenReturn(Flux.just(ownerWithPet(5, "Betty", "Davis", 20, "Leo")));

        client.get()
            .uri("/api/gateway/visits/upcoming")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].description").isEqualTo("checkup");
    }

    @Test
    void getUpcomingVisits_isABadRequestWhenVisitsServiceRejectsDays() {
        Mockito.when(visitsServiceClient.getUpcomingVisits(0))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST)));
        Mockito.when(customersServiceClient.getOwners()).thenReturn(Flux.empty());

        client.get()
            .uri("/api/gateway/visits/upcoming?days=0")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void getUpcomingVisits_isABadRequestWhenDaysIsNotANumber() {
        client.get()
            .uri("/api/gateway/visits/upcoming?days=seven")
            .exchange()
            .expectStatus().isBadRequest();
    }
}
