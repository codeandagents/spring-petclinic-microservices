package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TimeZone;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for docs/specs/upcoming-visits.md, written before the feature exists.
 * <p>
 * Three different todays on purpose: the clinic is in New Zealand, the server runs in India,
 * and the clock says 12:00 UTC on October 20. In the clinic it is already 01:00 on October 21.
 */
@SpringBootTest(properties = "petclinic.clinic.time-zone=Pacific/Auckland")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UpcomingVisitsAcceptanceTest {

    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    static final TimeZone ORIGINAL_SERVER_ZONE = TimeZone.getDefault();

    static {
        // Before the application starts: the server runs east of UTC, where rule R2's date bug shows.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterAll
    static void restoreServerZone() {
        TimeZone.setDefault(ORIGINAL_SERVER_ZONE);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void noOtherVisits() {
        jdbc.update("DELETE FROM visits");
    }

    @Test
    void listsSevenDaysStartingOnTheClinicsToday() throws Exception {
        visit(1, "2026-10-20", "yesterday, in the clinic");
        visit(2, "2026-10-21", "today");
        visit(3, "2026-10-27", "the 7th day");
        visit(4, "2026-10-28", "one day too late");

        mvc.perform(get("/visits/upcoming"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].description", contains("today", "the 7th day")))
            .andExpect(jsonPath("$.items[0].date").value("2026-10-21"))
            .andExpect(jsonPath("$.items[0].petId").value(7));
    }

    @Test
    void daysSetsHowManyDaysAreListed() throws Exception {
        visit(1, "2026-10-21", "today");
        visit(2, "2026-10-22", "tomorrow");

        mvc.perform(get("/visits/upcoming").param("days", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].description", contains("today")));
    }

    @Test
    void sortedByDateThenById() throws Exception {
        visit(3, "2026-10-23", "third");
        visit(2, "2026-10-21", "second");
        visit(1, "2026-10-21", "first");

        mvc.perform(get("/visits/upcoming"))
            .andExpect(jsonPath("$.items[*].description", contains("first", "second", "third")));
    }

    @Test
    void noUpcomingVisitsIsAnEmptyList() throws Exception {
        visit(1, "2026-10-20", "yesterday, in the clinic");

        mvc.perform(get("/visits/upcoming"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "32", "-1", "seven"})
    void daysOutsideOneTo31IsABadRequest(String days) throws Exception {
        mvc.perform(get("/visits/upcoming").param("days", days))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aVisitKeepsItsDateWhateverTheServersTimeZone() throws Exception {
        mvc.perform(post("/owners/1/pets/7/visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"2026-10-21\", \"description\": \"checkup\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.date").value("2026-10-21"));
        entityManager.flush();
        entityManager.clear();  // read it back from the database, not from the cache

        mvc.perform(get("/owners/1/pets/7/visits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value("2026-10-21"));
    }

    private void visit(int id, String date, String description) {
        jdbc.update("INSERT INTO visits (id, pet_id, visit_date, description) VALUES (?, 7, ?, ?)",
            id, java.sql.Date.valueOf(date), description);
    }
}
