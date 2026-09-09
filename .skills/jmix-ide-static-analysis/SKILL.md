---
name: jmix-ide-static-analysis
description: Gate-1 static checks. PRIMARY when connected: run a Jmix-aware IDE/semantic inspection (e.g. JetBrains get_file_problems) on every file you created/edited — it catches the *-view.xml defects a compiler cannot (unresolved msg://, invalid property paths, missing data containers). Fall back to compileJava plus mechanical descriptor checks when no inspection is available. Read before relying on any static check.
---

# Static analysis (Gate 1)

Gate 1 = every file you created or edited passes a static check before you move
on. **Run the IDE inspection first when it is connected; fall back to compile +
mechanical descriptor checks when it is not.**

## 1. Semantic / IDE inspection — PRIMARY when connected

If you have a Jmix-aware IDE/semantic inspection (e.g. JetBrains
`get_file_problems`), run it PER FILE on every `.java` and especially every
`*-view.xml` you touched — this is your primary Gate-1 check. It is the only STATIC
catch for the descriptor defects a compiler cannot see: unresolved `msg://` keys,
invalid property paths, missing data containers, broken component bindings (and it
flags the same Java errors a compile would). Rules when you use one:

- **Always pass `projectPath` — your project's absolute root (`pwd`).** The project
  you work in is opened as its own root, so `projectPath` = your current working
  directory. Pass it on EVERY JetBrains MCP call (`get_file_problems`,
  `get_project_modules`, `create_new_file`, …): with more than one project open the
  IDE cannot tell which you mean and the call fails — `No exact project is specified`
  or a malformed `-32602` response — so the gate silently does nothing. **If you do
  not know the root:** issue any JetBrains MCP call once WITHOUT `projectPath`; the
  error lists every open project as `Currently open projects: {"projects":[{"path":
  …}]}`. Pick the entry equal to (or the deepest one containing) your `pwd`, then
  reuse it as `projectPath` for all later calls.
- **Surface WARNINGS, not just errors.** Jmix-plugin findings (unresolved
  `msg://`, bad fetch/entity refs, broken bindings) are typically reported as
  WARNINGS — an errors-only view looks clean when it is not. Include warnings and
  treat Jmix-inspection / unresolved-reference warnings as blockers.
- **Never trust an EMPTY result you did not confirm.** An inspection that
  silently targeted the wrong project/module returns "no problems" on a file it
  never looked at — that is false-clean. Confirm the file was actually inspected
  before calling it clean.
- **The inspection needs the project opened as a STANDALONE Gradle project** (its
  own root, not a subdirectory of another open project) — a nested path resolves to
  the wrong module and returns generic noise (e.g. "URI is not registered"), not
  Jmix findings. Even when it works it can miss unknown components / bad attributes,
  so keep the mechanical descriptor checks alongside it.
- **Fallback decision rule.** If the inspection returns "URI is not registered" or
  only generic/non-Jmix findings on a file you KNOW has a descriptor (a `*-view.xml`
  you just edited), treat the inspection as UNAVAILABLE for this run — do NOT call
  the file clean. Fall through to `compileJava` + the mechanical descriptor checks.
- **Never trust an ALL-ERRORS result either — the IDE may not index the file.**
  If the inspection reports "Cannot resolve symbol" for practically EVERY symbol,
  including JDK types such as `String`, `List`, `Map`, that signature means "the
  IDE is not indexing this file" — the result is garbage, not a broken file. The
  bogus findings carry severity ERROR, so an errors-only filter does not remove
  them; do NOT fix code from them. Disregard the whole result and fall back to
  `compileJava` + the mechanical descriptor checks. For an XML-only edit, where
  there are no JDK types to look for, the analogue is "message not found" on
  `msg://` keys that grep DOES resolve in the module's `messages_*.properties`.
  The most common cause is a git worktree nested under the project root (agent
  tooling creates these routinely) while the IDE is opened on the main checkout:
  the file IS inside the project directory, so the inspection accepts the path
  and returns bogus findings instead of refusing loudly. Mechanical precondition
  for any file type: `git rev-parse --show-toplevel` run in the edited file's
  directory must equal `projectPath` — if it differs, the file lives in another
  worktree and the inspection of that path is not authoritative. The same
  signature also arises from a file outside any source root, a never-imported
  Gradle project, or indexing lag right after files were written from outside
  the IDE.
- **A compile-only fallback leaves debt — record it.** When no inspection is
  connected, LIST the files that got only `compileJava` in your completion report,
  and re-inspect them the next time a session has the inspection available. A green
  compile is not a substitute: it misses every Jmix semantic finding below.

### Jmix semantic findings only this inspection catches — fix them

These are TRUE positives. They are invisible to `compileJava`, to the mechanical
checks, and to a green `clean test`:

- **Unresolved `msg://` key** — renders as the literal key at runtime.
- **Invalid property path / missing data container** in a `*-view.xml`.
- **Unfetched-attribute risk** on a property a view reads but the fetch plan omits
  (see `jmix-configure-fetch-plan`).
- **`Result of DataManager.save() is not used; use saveWithoutReload()`** —
  `save()` re-selects the entity after persisting; when the caller discards the
  result that reload is wasted work. See `jmix-create-service`.

### Known false positives — do NOT chase these

Expected noise; state them as understood and move on:

| Finding | Why it is not a defect |
|---|---|
| "Method is never used" on `@ConfigurationProperties` getters/setters | called by Spring through reflection |
| "Method is never used" on entity getters/setters | called by Jmix/JPA through reflection and by view descriptors |
| "Unused property" on an i18n key | normal when the view that references it does not exist yet (e.g. a detail-view title key added while building the list view) |
| "Field can be converted to a local variable" in a test class | fields hold `@Autowired`/fixture state across methods |
| "Result of `DataManager.save()` is not used" in a TEST that keeps the entity for cleanup | only a false positive if the returned instance IS used (e.g. added to a cleanup list); if the result is truly discarded it is the real finding above |

### What NO static check covers

- A CSS custom property that does not exist in the app's active theme. A
  `--lumo-*` variable in an Aura-themed app is undefined: the browser silently
  falls back (color → inherited, `border-radius` → 0), nothing errors, and every
  gate stays green. Only reading the COMPUTED style in a real browser catches it.
  See `jmix-style-ui`.
- Code paths that only run outside a user request — schedulers, `@Async`,
  message listeners. See `jmix-run-background-code`.

## 2. Compile — Java ground truth, and floor when no inspection

```bash
./gradlew --no-daemon compileJava
```

Authoritative for `.java`: unresolved symbols, wrong imports/packages, type
mismatches, bad handler signatures. Run it regardless — it is cheap and is the
precondition for Gate 2. But **compileJava is BLIND to XML descriptors.** A
`*-view.xml` with an enum bound to `entityComboBox` instead of `comboBox`, an
`itemsQuery` without `:searchString`, a `msg://` typo, or an action opening a
non-existent view id compiles perfectly clean and then throws at render time. A
clean compile proves NOTHING about any `.xml`, and a 0-byte `.java`/`.xml` also
compiles clean — confirm written files are non-empty.

## 3. Mechanical descriptor checks — floor when no inspection

When you have no semantic inspection, these are your static floor for the
render-time defect classes. Run them from the project directory; each maps to a
defect that passes a clean compile (and even a green `clean test`).

Two kinds below: the `find` checks are pass/fail (any output = a defect to fix);
the `grep` checks only SURFACE candidates — a non-empty result is not
automatically a failure, but you MUST explain every hit (e.g. each `msg://` key
must actually resolve in a `messages_*.properties`; each `= :` loader param must
be bound/guarded).

For `*-view.xml`, the file must start cleanly with an XML tag: either
`<?xml ...?>` or `<view ...>`. Any BOM, whitespace, or stray content before the
first `<` is a defect; it can surface as `SAXParseException: Content is not
allowed in prolog`.

```bash
# package line on every new .java (missing → view-registry / import breakage)
find src/main/java -name '*.java' | while read f; do head -1 "$f" | grep -q '^package ' || echo "MISSING package: $f"; done
# every @NotNull / nullable=false needs an entity-layer default, not InitEntityEvent
grep -rn "nullable = false\|@NotNull" src/main/java --include='*.java'
# manual :param loaders must be bound (:container_* / :component_*) or guarded
grep -rn "= :" src/main/resources --include='*-view.xml'
# CREATE ⇒ MODIFY in every role; @MenuPolicy lists leaf item ids
grep -rn "CREATE\|MODIFY\|VIEW" src/main/java --include='*Role.java'
# itemsQuery must reference :searchString (or switch to itemsContainer)
grep -rn "itemsQuery" src/main/resources --include='*-view.xml'
# no raw Vaadin Dialog in a Jmix view
grep -rn "com.vaadin.flow.component.dialog.Dialog" src/main/java --include='*.java'
# every msg:// key must resolve in a messages_*.properties (else literal key renders)
grep -rhoE 'msg://[^"<> ]+' src/main/resources --include='*.xml' | sort -u
# no BOM / leading content before the XML tag (else SAX "Content is not allowed in prolog")
find src/main/resources -name '*-view.xml' | while read f; do LC_ALL=C head -c 1 "$f" | grep -q '<' || echo "BAD XML prolog/BOM: $f"; done
# no 0-byte source file (empty role drops policies; empty *-view.xml poisons registry)
find src/main -type f \( -name '*.java' -o -name '*.xml' \) -size 0
```

These are MANDATORY whenever you have neither an inspection nor a Gate-3 render
walk — they are then your ONLY catch for those defects.

## Per-file loop

For each file you created or edited: inspection-if-connected (else compile) → fix
every error and every unresolved-reference / Jmix-inspection warning → repeat until
clean. Run it on EVERY file, not a sample — the defects that survive are the ones
you were confident about and never checked. After the last edit, a full
`compileJava` is the precondition for Gate 2 (`clean test`).

For verifying a symbol BEFORE you type an unfamiliar API, see `jmix-verify-api-symbol`
— the cheapest, earliest defense against hallucinated names.
