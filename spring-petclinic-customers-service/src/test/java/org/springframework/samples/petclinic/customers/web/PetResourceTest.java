package org.springframework.samples.petclinic.customers.web;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.customers.model.Owner;
import org.springframework.samples.petclinic.customers.model.OwnerRepository;
import org.springframework.samples.petclinic.customers.model.Pet;
import org.springframework.samples.petclinic.customers.model.PetRepository;
import org.springframework.samples.petclinic.customers.model.PetType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Maciej Szarlinski
 */
@WebMvcTest(PetResource.class)
@ActiveProfiles("test")
class PetResourceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PetRepository petRepository;

    @MockitoBean
    OwnerRepository ownerRepository;

    @Test
    void shouldGetAPetInJSonFormat() throws Exception {

        Pet pet = setupPet();

        given(petRepository.findById(2)).willReturn(Optional.of(pet));


        mvc.perform(get("/owners/2/pets/2").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.name").value("Basil"))
            .andExpect(jsonPath("$.type.id").value(6));
    }
    
    @Test
    void shouldReturnNotFoundWhenPetDoesNotExist() throws Exception {
        given(petRepository.findById(99)).willReturn(Optional.empty());

        mvc.perform(get("/owners/2/pets/99").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingAPetWithAFutureBirthDate() throws Exception {
        Pet pet = setupPet();
        given(ownerRepository.findById(1)).willReturn(Optional.of(pet.getOwner()));

        mvc.perform(post("/owners/1/pets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":0,\"name\":\"Future\",\"birthDate\":\"2999-01-01\",\"typeId\":2}"))
            .andExpect(status().isBadRequest());

        verify(petRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdatingAPetWithAFutureBirthDate() throws Exception {
        Pet pet = setupPet();
        given(petRepository.findById(2)).willReturn(Optional.of(pet));

        mvc.perform(put("/owners/1/pets/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":2,\"name\":\"Basil\",\"birthDate\":\"2999-01-01\",\"typeId\":6}"))
            .andExpect(status().isBadRequest());

        verify(petRepository, never()).save(any());
    }

    @Test
    void shouldUpdateAPetThroughTheUrlIdentifiers() throws Exception {
        Pet pet = setupPet();
        PetType hamster = new PetType();
        hamster.setId(6);
        given(petRepository.findById(2)).willReturn(Optional.of(pet));
        given(petRepository.findPetTypeById(6)).willReturn(Optional.of(hamster));

        mvc.perform(put("/owners/1/pets/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":2,\"name\":\"Renamed\",\"birthDate\":\"2012-08-05\",\"typeId\":6}"))
            .andExpect(status().isNoContent());

        verify(petRepository).save(pet);
    }

    @Test
    void shouldRejectUpdateWhenBodyIdDiffersFromUrlPetId() throws Exception {
        given(petRepository.findById(2)).willReturn(Optional.of(setupPet()));
        given(petRepository.findById(3)).willReturn(Optional.of(setupPet()));

        mvc.perform(put("/owners/1/pets/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":3,\"name\":\"Hijack\",\"birthDate\":\"2012-08-05\",\"typeId\":6}"))
            .andExpect(status().isBadRequest());

        verify(petRepository, never()).save(any());
    }

    @Test
    void shouldNotUpdateAPetThroughAnotherOwnersUrl() throws Exception {
        given(petRepository.findById(2)).willReturn(Optional.of(setupPet())); // owned by owner 1

        mvc.perform(put("/owners/99/pets/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":2,\"name\":\"Hijack\",\"birthDate\":\"2012-08-05\",\"typeId\":6}"))
            .andExpect(status().isNotFound());

        verify(petRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreatingAPetWithAnUnknownType() throws Exception {
        given(ownerRepository.findById(1)).willReturn(Optional.of(setupPet().getOwner()));
        given(petRepository.findPetTypeById(999)).willReturn(Optional.empty());

        mvc.perform(post("/owners/1/pets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":0,\"name\":\"Typeless\",\"birthDate\":\"2020-01-01\",\"typeId\":999}"))
            .andExpect(status().isBadRequest());

        verify(petRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdatingAPetWithAnUnknownType() throws Exception {
        given(petRepository.findById(2)).willReturn(Optional.of(setupPet()));
        given(petRepository.findPetTypeById(999)).willReturn(Optional.empty());

        mvc.perform(put("/owners/1/pets/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":2,\"name\":\"Basil\",\"birthDate\":\"2012-08-05\",\"typeId\":999}"))
            .andExpect(status().isBadRequest());

        verify(petRepository, never()).save(any());
    }

    private Pet setupPet() {
        Owner owner = new Owner();
        owner.setFirstName("George");
        owner.setLastName("Bush");
        ReflectionTestUtils.setField(owner, "id", 1);

        Pet pet = new Pet();

        pet.setName("Basil");
        pet.setId(2);

        PetType petType = new PetType();
        petType.setId(6);
        pet.setType(petType);

        owner.addPet(pet);
        return pet;
    }
}
