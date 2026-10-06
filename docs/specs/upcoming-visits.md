# Spec: upcoming visits for the front desk

| | |
|---|---|
| Status | Approved, 2026-10-06 |
| Architect | Arun Joseph |
| Acceptance tests | `spring-petclinic-visits-service/src/test/java/org/springframework/samples/petclinic/visits/web/UpcomingVisitsAcceptanceTest.java` (committed with this spec; they fail until the feature exists) |

## Why

The front desk starts every day with one question: who is coming in? Today the app can only show
visits pet by pet, on each owner's page.

## What

1. **A page, "Upcoming visits"**, linked from the nav bar. It lists the visits from the clinic's
   today through the next 6 days (7 days in all): date, pet, owner (a link to the owner's page) and
   description. With no visits it says "No visits in the next 7 days."
2. **visits-service: `GET /visits/upcoming?days=N`**
   - `days` = how many days, today included: 1 to 31, default 7. Anything else: 400 Bad Request.
   - Response: `{"items": [{"id", "date", "description", "petId"}]}`, the same item shape as
     `GET /pets/visits`.
3. **api-gateway: `GET /api/gateway/visits/upcoming?days=N`**, for the page.
   - Each item: `id`, `date`, `description`, `petId`, `petName`, `ownerId`, `ownerName`
     (first and last name). Pet and owner come from customers-service.
   - A `days` value that visits-service rejects is a 400 here too.

## Rules

- **R1. Whose today: the clinic's.** visits-service has a setting `petclinic.clinic.time-zone`
  (an IANA zone id such as `Asia/Kolkata`; default `UTC`). It takes the current instant from a
  `java.time.Clock` bean, so tests can fix it, and converts that instant to a date in the clinic's
  zone. Not the server's zone, not the browser's, and the Clock's own zone doesn't matter.
- **R2. A visit date is a calendar date**: no time of day, no zone. A visit booked for
  2026-10-21 is stored, returned and shown as 2026-10-21 by every service, whatever time zone the
  JVM runs in. (Today it isn't: on a JVM east of UTC, a visit saved as 2026-10-21 comes back as
  2026-10-20. This feature fixes that.)
- **R3. The window**: from today to today + (days - 1), both ends included.
- **R4. Order**: by date, then by visit id.

## Out of scope

Vets on visits, paging, editing or cancelling visits, security, the MySQL schema (`visit_date` is
already a `DATE` column), the GenAI service.

## Done when

- The acceptance tests pass, and so does the whole build:
  `./mvnw test -pl '!spring-petclinic-genai-service'`.
- The gateway endpoint has tests of its own.
- On the running app, the page lists visits added for the coming days.
