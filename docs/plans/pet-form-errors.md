# Pet form: show validation errors (upstream #557)

## Problem and root cause
Backend validation is present and tested: `PetRequest.birthDate` has `@PastOrPresent`, `name` has `@Size(min=1)`, and both `PetResource` create and update use `@Valid` (400 on failure). `PetResourceTest` already covers future birth date on create and update. The backend is not the bug.

The gateway does not swallow the 400. The default filters are CircuitBreaker and Retry, but 4xx is not a failure for them (retry only on 503 for POST, and the fallback is only on errors or open circuits). The response reaches the browser unchanged.

The bug is in the frontend:
- `pet-form.controller.js` `submit()` has `req.then(...)` with no error handler, and nothing is shown on failure.
- The global `HttpErrorHandlingInterceptor` (`scripts/infrastructure/httpErrorHandlingInterceptor.js`) assumes the old body shape, `error.errors.map(...)`. The Spring Boot default error body is `{timestamp,status,error,path}`, and `errors` only appears if `server.error.include-binding-errors` is set. That setting lives in the external config repo, so it cannot be relied on. Without `errors`, the interceptor throws a TypeError, so no alert appears. The interceptor also returns `response` instead of `$q.reject`, which hides the failure from callers.
- The template validation hints are wrong: the birth-date hint tests `petForm.name.$error`, the form has no `name="petForm"`, and the date input has no `name`. There is no client-side future-date check.

## Decision
Options:
- (a) Make the backend return field errors, and have the UI map them.
- (b) Make the UI robust and add a client-side check.
- (c) Both.

Choose (c), kept small. The 400 body is not a stable contract (it depends on external config), so the backend gets a small `@RestControllerAdvice` in customers-service. It returns a `ProblemDetail`-style body for `MethodArgumentNotValidException`: `{status, error, errors:[{field, defaultMessage}]}`. That is the shape the existing interceptor already expects. The UI then shows the messages inline on the pet form, and the interceptor is made defensive. No new dependencies.

## Files to change
- customers-service, new `web/RestExceptionHandler.java`: handles `MethodArgumentNotValidException` and returns 400 with the `errors` list. Make the birth-date message readable, e.g. `@PastOrPresent(message = "Birth date cannot be in the future")` and a message on `@Size` in `PetRequest`.
- api-gateway `pet-form.controller.js`: add a `.catch` that sets `self.errors` (field to message, with a generic fallback "Could not save the pet" for other statuses, including 503). Do not navigate on failure.
- api-gateway `pet-form.template.html`: give the form `name="petForm"` and the date input `name="birthDate"`. Fix the wrong `$error` reference. Add `max` (today) on the date input. Render `$ctrl.errors` in an alert block.
- api-gateway `httpErrorHandlingInterceptor.js`: guard against a missing `errors`. Return `$q.reject(response)`. Do not alert for the pet form, which shows inline errors, so skip when the request is handled locally. Simplest: the interceptor only alerts when there is no `errors` and the status is 5xx or 0.

## Tests (write first)
- customers-service `PetResourceTest`: extend the existing future-date create and update tests to assert the JSON body, `$.errors[0].field == "birthDate"` and the message. Add a blank-name case.
- api-gateway: no JS test setup exists. Add a gateway test (WebTestClient or the existing mock-server style) only if cheap, proving a downstream 400 body passes through the route unchanged. Otherwise the frontend is covered by the manual check below. Do not add a JS test framework (out of scope).

## Manual verification (running app)
Start the stack (`./scripts/run_all.sh`), open http://localhost:8080, then Owners, an owner, Add New Pet.
1. Enter a future birth date and submit: an inline error "Birth date cannot be in the future" appears, and the page does not navigate.
2. Repeat on Edit Pet.
3. Submit a blank name: a name error appears.
4. Submit a valid pet: it saves and navigates to the owner page.
5. Stop customers-service and submit: a generic "could not save" message appears.
Also `curl -i -X POST localhost:8080/api/customer/owners/1/pets -H 'Content-Type: application/json' -d '{"id":0,"name":"x","birthDate":"2999-01-01","typeId":1}'` shows 400 with `errors`.

## Out of scope
Owner form errors, a JS test framework, changing the external config repo, other services' error formats, and gateway circuit-breaker or retry config.
