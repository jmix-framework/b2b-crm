---
name: jmix-verify-bootrun
description: Gate 2 (mandatory) — verify the Spring/Jmix context loads via a TERMINATING `gradle clean test`, never a long-running bootRun. Gate 3 (optional) — if you have a browser/UI-automation tool, walk the views/buttons/fields you created to catch render-time defects that compile and clean test miss. A green compile/clean test is NOT proof a view renders.
---

# Gate 2 (context load) + Gate 3 (optional render walk)

## When to use

After writing your Jmix artifacts (entities, views, services, roles, …):
- **Gate 2 (mandatory):** confirm the Spring/Jmix context loads cleanly.
- **Gate 3 (optional):** confirm each view you created actually renders.

These run AFTER static analysis. Treat inspection findings (from MCP
inspection tools when connected, your IDE otherwise) as WARNINGS to resolve
first — and remember an EMPTY inspection result you did not confirm is
false-clean, not a pass.

## Gate 2 — `clean test` (NEVER bootRun)

Run the project's terminating context-load test:

```bash
./gradlew --no-daemon clean test
```

It boots the Spring/Jmix context, runs the project's seed tests, and EXITS.
Confirm `BUILD SUCCESSFUL` and no failed tests. Also confirm tests actually
ran: `Tests run: 0` (or no test task at all) means the context was never
booted and Gate 2 did NOT pass — ensure at least one context-load test exists
and executed.

**NEVER use `./gradlew bootRun` (or any non-terminating server start) as the
Gate-2 check.** bootRun does not exit — it hangs your turn and leaves the HTTP
port locked by a zombie process. bootRun is not a gate; Gate 2 is `clean test`.

### When the first Gate 2 failure hides the real cause

The Gradle console tail may show only a wrapper exception such as
`RuntimeException at HikariConfig.java:514`. Read the generated JUnit XML before
diagnosing the failure:

```bash
rg -n -C 3 "Failed to load driver class|Caused by" \
  build/test-results/test/*.xml
```

One known failure in a freshly generated project is:

```text
Failed to load driver class org.hsqldb.jdbc.JDBCDriver in either of
HikariConfig class loader or Thread context classloader
```

If `src/test/resources/application-test.properties` configures HSQLDB but
`build.gradle` declares only the production database driver, the test runtime is
incomplete. Add the HSQLDB test-runtime driver:

```groovy
testRuntimeOnly 'org.hsqldb:hsqldb'
```

Then rerun `./gradlew --no-daemon clean test`. Do not change the test datasource
or investigate unrelated application code when the XML report shows this exact
missing-driver failure.

If the test suite passed before your changes and a seed test is now RED, assume
you broke it and fix it to green. Do not call that failure "pre-existing". On the
first Gate 2 run of a freshly generated project, inspect the JUnit XML and build
dependencies before deciding whether your changes caused the failure.

Common causes when a seed test goes red after your change:

- **`NoSuchViewException` after you added views** → you broke the VIEW REGISTRY;
  it scans all `@ViewController` classes at startup and one broken view poisons
  navigation to EVERY view, including pre-existing ones. Check, in order: (1) every
  new view `.java` has a `package` line matching its directory — a class in the
  default package registers its `@Route`/`@ViewController` wrong; (2) no two
  `@ViewController(id=…)` share an id; (3) every `@ViewDescriptor` path resolves
  to a real XML next to the class; (4) no `*-view.xml` is empty or malformed — an
  empty descriptor throws `SAXParseException: Premature end of file` and poisons
  the registry.
- **`MetaClass not found for class X`** → the entity is missing `@JmixEntity`, or
  its package is outside the application scan root.
- **`ConstraintViolationException` on save** → a `@NotNull` persistent field has
  no value on the `DataManager` path (see `jmix-create-entity`).

A test that goes red and you cannot explain is a blocker, never a footnote in
your "done" summary.

A green Gate 2 is necessary but NOT sufficient: the seed tests load the context
but do NOT open your new views, exercise your new roles, or fire code that only
runs outside a user request (see `jmix-run-background-code`). It catches catastrophic 
breakage (broken view registry, schema/Liquibase error, missing `@JmixEntity`), 
not render-time UI defects — those are caught by the mechanical checks and Gate 3.

The rule behind it: a defect survives every gate when nothing enters its code
path. So when you add a path that nothing yet calls, ADD THE CALLER — a test or a
render walk — or report it as unverified. Never write "all gates passed" for a
path no gate entered.

## Gate 3 — render walk (REQUIRED for every view/role you created or changed)

Gate 3 is the ONLY gate that opens your new views/roles at runtime (Gate 2 does
not), so it is NOT optional when you created or changed one. Per such view, pick
ONE:
- a browser/UI-automation tool is connected → render-walk it (below); or
- no browser tool → write a minimal headless `@UiTest` that navigates to the
  view and asserts it opens (see `jmix-create-test`).

Do not write "no browser tool" without checking the connected tools. Only if a
view can be reached by NEITHER a browser tool NOR a `@UiTest`, skip it — and then
report `render NOT verified: <view>`, never "all gates passed".

**Prerequisite — Spring Boot Actuator.** The readiness probe below polls
`/actuator/health`, so the project must include the Actuator starter and expose
the `health` endpoint:

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

```properties
# application.properties
management.endpoints.web.exposure.include=health
```

Jmix projects do NOT bundle Actuator by default — add the dependency and
property above (verify with `./gradlew dependencies | grep actuator`). If you
cannot add/configure it, use the `@UiTest` path instead, or an alternative
readiness signal: tail the boot log for `Started <App>Application in ...`.

**Before you start it — decide which database the walk writes into.** A walk of a
data-driven view usually has to CREATE data: the view shows nothing until
reference records, their parents, and the child rows that give them amounts all
exist. Those records are written by the walk and left behind, and there is no
undo step below. The configured development datasource is frequently the
developer's own working dataset, so choose deliberately:

- **A throwaway datasource — the default choice.** Select it on the run command
  line so the properties file is never touched:
  ```bash
  nohup ./gradlew --no-daemon bootRun \
        --args="--main.datasource.url=jdbc:hsqldb:file:/tmp/renderwalk" > /tmp/jmix_app.log 2>&1 &
  ```
  Do NOT edit `application.properties` to point it elsewhere: that file is
  tracked, and it often carries a local change of the developer's that you would
  then be committing or reverting by accident.
- **The configured development database — only for a read-only walk**, where
  every view you open already has the data it needs and you create nothing.

The fixture data is a side effect you own. Report what you created and where it
landed.

If you do have one, run the mechanical checks first, then:

**If the application is already running** — the owner started it, possibly on a
non-default port — take that base URL and port as given, do the walk against it,
and do NOT run steps 1, 2 or 4: never start a second instance, and never shut down
a process you do not own (the "leave the port free" in step 4 is only for the case
where you started it yourself). Note in the report that the app was not started by
the gate. The numbered steps below are for when you start and own the process.

1. Start the app in the BACKGROUND so it does not block your turn, capturing its
   log — e.g. `nohup ./gradlew --no-daemon bootRun > /tmp/jmix_app.log 2>&1 &`,
   adding the datasource override above unless the walk is read-only.
2. Poll readiness on the Spring Boot Actuator health endpoint, then proceed — do
   not wait on the (non-terminating) process:
   ```bash
   curl --retry-connrefused --retry 40 --retry-delay 2 -sf -m 5 \
        http://localhost:8080/actuator/health && echo READY
   ```
   Use `/actuator/health` — Spring Boot does not expose a bare `/health`. If it
   never becomes ready, tail the log, skip Gate 3, and shut the process down.
3. With your browser/UI-automation tool, navigate to each view you created, click
   each button/action, fill each field, and confirm no error overlay or server
   exception. If the application accepts file uploads, select and upload a real
   file through the browser, then confirm the UI reports success and the file can
   be opened or otherwise read back.
   A Jmix UI is a Vaadin web-component UI, so it does not respond to a browser
   tool the way a plain HTML page does — see the next section.
   Drive the walk **only through real input events** — a genuine click,
   real typing, `Enter`. Never assign a component's `.value` or set a component
   property from the page or console: in a server-driven UI the server holds the
   component state, so a scripted mutation never reaches it and can desync the
   session, silently invalidating the gate (the app bounces to the login view
   mid-walk with nothing in the browser console, so it reads as an environment
   problem rather than a self-inflicted one). Reading the DOM to assert what
   rendered is fine; mutating it is not.
   exception. 
4. **Shut the background app down** when finished (`kill` the bootRun PID; if the
   port stays held, kill whatever holds it). Always leave the port free, and state
   in your report which database the walk ran against and what data it created
   there.

### Driving a Jmix/Vaadin UI

The page is built from `vaadin-*` custom elements, rendered by a client that keeps
working after the HTTP response is done:

- **Wait for the client to render before you look.** The first snapshot or
  screenshot after navigation is routinely blank; retry until the expected content
  is there instead of reporting a render failure.
- **Refs go stale after every action.** Retake the snapshot immediately before each
  interaction rather than reusing refs captured earlier in the walk.
- **Role/name locators often miss `vaadin-*` components.** Prefer a ref from the
  current snapshot, falling back to a DOM query on tag name and visible text.
- **A disabled action is usually a precondition, not a defect.** Jmix actions enable
  on state — find and satisfy what this one waits for before reporting it broken.

### File uploads in a non-root container

If the deliverable includes a container image, a local upload does not verify the
container filesystem permissions. Run the upload check against the built image or
the deployed application.

For an image that runs as a non-root `app` user with `/app` as its working
directory, create the Jmix temporary directory and give the application user
ownership before switching to that user:

```dockerfile
RUN mkdir -p /app/.jmix/temp && chown -R app:app /app/.jmix
```

## Honest scope — Gate 3 is a render gate

A browser walk catches the gross render exceptions a user would hit, but the
test suite may exercise a different, headless server-side code path
(`jmix-flowui-test-assist` `@UiTest`). A defect can therefore pass in the
browser and still fail a test (e.g. a required default set only in the view, or
a non-standard save-action id). Never let a green render walk override the
mechanical checks.
