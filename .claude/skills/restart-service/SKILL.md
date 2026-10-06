---
name: restart-service
description: Rebuild and restart one PetClinic service (customers-service, vets-service, visits-service or api-gateway) on this Windows machine, so a code change can be tried on the running app. Use it whenever a running service has to pick up new code, instead of stopping and starting java processes by hand.
argument-hint: <service>
---

# Restart a PetClinic service

Run the bundled script once per service, with the service's name:

```
powershell -NoProfile -ExecutionPolicy Bypass -File "${CLAUDE_SKILL_DIR}/scripts/restart-service.ps1" customers-service
```

Which service to restart:

- Java or config changes in `spring-petclinic-<name>` -> that service.
- Anything in `spring-petclinic-api-gateway`, including the web UI under `src/main/resources/static`
  -> `api-gateway` (the UI is served from its jar, so a browser refresh alone shows the old page).
- config-server and discovery-server are never restarted this way.

What the script does: removes the service's old instance from Eureka, stops its java process,
rebuilds its jar (`mvnw package -DskipTests`), starts it, waits until it is registered, and waits
until the gateway has answered 10 calls in a row through it. Removing the old instance first matters:
a killed instance stays in Eureka for about 90 seconds and the gateway keeps sending calls to it.

It takes one to two minutes per service, so give the command a timeout of at least 5 minutes.
It ends with `ready`; anything else is a failure. On a failure, read
the log it names (`%TEMP%\petclinic-<service>.log`) before trying again, and tell the user what
went wrong. Never report a service as restarted unless the script said `ready`.
