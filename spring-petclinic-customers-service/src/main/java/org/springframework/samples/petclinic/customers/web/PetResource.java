/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.customers.web;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.customers.model.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Maciej Szarlinski
 * @author Ramazan Sakin
 */
@RestController
@Timed("petclinic.pet")
class PetResource {

    private static final Logger log = LoggerFactory.getLogger(PetResource.class);

    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;

    PetResource(PetRepository petRepository, OwnerRepository ownerRepository) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
    }

    @GetMapping("/petTypes")
    public List<PetType> getPetTypes() {
        return petRepository.findPetTypes();
    }

    @PostMapping("/owners/{ownerId}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public Pet processCreationForm(
        @Valid @RequestBody PetRequest petRequest,
        @PathVariable("ownerId") @Min(1) int ownerId) {

        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner " + ownerId + " not found"));
        final PetType type = findPetTypeById(petRequest.typeId());

        final Pet pet = new Pet();
        owner.addPet(pet);
        return save(pet, petRequest, type);
    }

    @PutMapping("/owners/{ownerId}/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void processUpdateForm(
        @Valid @RequestBody PetRequest petRequest,
        @PathVariable("ownerId") int ownerId,
        @PathVariable("petId") int petId) {

        if (petRequest.id() != petId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Pet id " + petRequest.id() + " in the body does not match " + petId + " in the URL");
        }
        Pet pet = findPetById(petId);
        if (!Objects.equals(pet.getOwner().getId(), ownerId)) {
            throw new ResourceNotFoundException("Pet " + petId + " not found for owner " + ownerId);
        }
        save(pet, petRequest, findPetTypeById(petRequest.typeId()));
    }

    private PetType findPetTypeById(int typeId) {
        return petRepository.findPetTypeById(typeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet type " + typeId + " not found"));
    }

    private Pet save(final Pet pet, final PetRequest petRequest, final PetType type) {

        pet.setName(petRequest.name());
        pet.setBirthDate(petRequest.birthDate());
        pet.setType(type);

        log.info("Saving pet {}", pet);
        return petRepository.save(pet);
    }

    @GetMapping("owners/*/pets/{petId}")
    public PetDetails findPet(@PathVariable("petId") int petId) {
        Pet pet = findPetById(petId);
        return new PetDetails(pet);
    }


    private Pet findPetById(int petId) {
        return petRepository.findById(petId)
            .orElseThrow(() -> new ResourceNotFoundException("Pet " + petId + " not found"));
    }

}
