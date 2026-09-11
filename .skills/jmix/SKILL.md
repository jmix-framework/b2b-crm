---
name: jmix
description: Read FIRST before writing or changing any file in a Jmix application. Lists the project stack, maps the task to Jmix artifacts, routes each artifact to the skill that governs it, and names the checks that close a task. Your Jmix/Vaadin priors are the main source of wrong API names and broken descriptors — start here, not from memory.
---

# Working on a Jmix application

## Project stack

- Java 21
- Jmix 3, Spring Boot 4, Vaadin 25
- Gradle
- Relational database with Liquibase migrations

## Step 0 — map the task to artifacts, READ the matching skill BEFORE writing

The most common cause of defects is writing a Jmix artifact from memory instead
of from the rule that governs it. Your Jmix/Vaadin priors are the single biggest
source of wrong API names and broken descriptors.

Before writing a single file:

1. List every artifact the task implies — entities, enums, list views, detail
   views, composition children, services, event listeners, resource roles,
   changelogs, menu entries, message bundles, scheduled/background entry points.
2. For EACH artifact, READ the matching skill in **Skill routing** before you
   write it.
3. Only then start writing.

The verification skills (`jmix-ide-static-analysis`, `jmix-verify-bootrun`) are
gates, not how-to. They do not replace the artifact skill.

## Skill routing

READ the most specific skill for each artifact:

- Verify a Jmix/Vaadin API: `jmix-verify-api-symbol`
- Static checks / inspections / mechanical floor: `jmix-ide-static-analysis`
- Gate-2 context-load test (+ optional Gate-3 render walk): `jmix-verify-bootrun`
- Persistent entity: `jmix-create-entity`
- Enum used by an entity: `jmix-create-enum`
- List view: `jmix-create-list-view`
- Detail view: `jmix-create-detail-view`
- Parent-child composition editing (property-bound container, NO query loader): `jmix-create-composition-detail-view`
- Service-layer business logic: `jmix-create-service`
- Code running outside a user request (Quartz/`@Scheduled` job, `@Async` method, startup runner, message listener): `jmix-run-background-code`
- Detail dialog from a button/action, OR master-row selection → filtered child grid: `jmix-add-dialog-detail-flow`
- Entity lifecycle/event business logic: `jmix-add-entity-event-listener`
- Database schema: `jmix-create-liquibase-changelog`
- Resource roles: `jmix-create-resource-role`
- User-visible text / entity-enum captions: `jmix-add-i18n-keys`
- Tests: `jmix-create-test`
- Fetch plans / unfetched-reference / N+1 tuning: `jmix-configure-fetch-plan`
- DTO / non-persistent UI-bound model: `jmix-create-dto-entity`
- Reusable Flow UI fragment: `jmix-create-fragment`
- Component styling / theme tokens (`--aura-*`, `--lumo-*`) / CSS classes: `jmix-style-ui`

## A skill's framework rule beats sample code in a plan or brief

When a plan, brief, issue, or hand-off note carries verbatim code, that code is a
SUGGESTION. The skill's rule is the authority. Sample code in a plan is written
without the skill open, so it drops framework details that look optional and are
not — and because it arrives as "the code to write", implementers paste it over
the correct pattern without noticing.

So: when you are handed code to write, READ the skill for that artifact and
reconcile the two BEFORE writing. Where they disagree, the skill wins — and say in
your report that you diverged from the sample and why. Check especially entity
annotations, view descriptors, fetch plans, and lifecycle-method overrides.

## Cross-cutting checklist for a new entity / view

For each new persistent entity, run through: `jmix-create-entity` +
`jmix-create-liquibase-changelog` + `jmix-create-resource-role` +
`jmix-add-i18n-keys`. For a user-facing entity, also add a list and/or detail
view (`jmix-create-list-view`, `jmix-create-detail-view`) and a view policy in
every role that can open them — **including dialog-only detail views opened
from a composition table**.

Service- or listener-level defaulting does NOT relieve the entity from
defaulting required fields on initial persist — defaults must work through
`DataManager.create()` + `DataManager.save()` directly (tests bypass the view
layer). See `jmix-create-entity`.

## Closing a task — three gates

A task is NOT done after the code compiles. Three gates, in order. READ the skill
that owns each gate rather than running it from memory, and never assert a gate
passed without showing the evidence.

| Gate            | What it proves                                                                  | Skill that owns it                                  |
|-----------------|---------------------------------------------------------------------------------|-----------------------------------------------------|
| 1 API & static  | every Jmix/Vaadin symbol exists, and every file you wrote is free of static defects | `jmix-verify-api-symbol`, `jmix-ide-static-analysis` |
| 2 Context loads | the Spring/Jmix context boots, Liquibase runs, and the project tests pass        | `jmix-verify-bootrun`                               |
| 3 Render        | every view, button, and field you created actually renders                      | `jmix-verify-bootrun`                               |

NEVER use `bootRun` (or any non-terminating server start) as the Gate-2 check —
it does not exit and will hang your turn. Gate 2 is `clean test`.

Emit the evidence in your completion report. Per file you touched: its
static-check verdict. Per view/button/field you created: how you verified it
(inspection, mechanical check, or render walk). "BUILD SUCCESSFUL, all done"
with no per-file check and no render evidence is a non-answer.

## File-write trap

Always pass absolute paths to file-writing tools; in nested-project layouts the
working directory may not be what you assume. After a batch of writes, `ls` the
path you intended AND confirm each file is NON-EMPTY — a tool that silently
writes a 0-byte file leaves a defect that compile and `clean test` will NOT
catch (an empty role class drops all its policies; an empty `*-view.xml`
poisons the view registry). If a file is missing or empty, find and rewrite
it; do NOT `rm -rf` to "clean up".

Never edit generated frontend files — they are regenerated on every build.
