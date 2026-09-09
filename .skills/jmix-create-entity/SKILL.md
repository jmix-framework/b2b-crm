---
name: jmix-create-entity
description: Create or change a persistent Jmix JPA entity and its required surrounding artifacts.
---

# Create Persistent Entity

Use this skill when adding or changing a database-backed Jmix entity.

## Steps

1. Create or update the Java entity in `src/main/java/<base-package>/entity`.
   **Changing an existing entity? Read "Widening an existing entity" below first.**
2. Add Jmix and JPA metadata: `@JmixEntity`, `@Entity`, `@Table`.
3. **Choose the id strategy explicitly** — generated surrogate or assigned natural
   key. See "Id strategy" below.
4. Add `@Version`.
5. Add `@InstanceName` on a stable human-readable field or method.
6. Define columns with exact `nullable`, `length`, `precision`, and `scale` constraints from requirements.
7. Use `FetchType.LAZY` for relationships.
8. Create the Liquibase changelog using `jmix-create-liquibase-changelog`.
9. Add entity and attribute message keys using `jmix-add-i18n-keys`.
10. Add or update views and security roles if the entity is user-facing.
11. Before finishing, compare every required constraint against the source requirements and the Liquibase changelog.

## Entity Template

```java
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@JmixEntity
@Table(name = "CUSTOMER")
@Entity
public class Customer {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @InstanceName
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    // getters and setters
}
```

## Id strategy — an explicit choice, not a default

Two strategies. Decide which one the entity needs BEFORE writing the `@Id` field;
the wrong one is expensive to change once rows and changelogs exist.

**Generated surrogate UUID** — the default for entities this application owns.
Jmix assigns the id on `DataManager.create()`:

```java
@JmixGeneratedValue
@Column(name = "ID", nullable = false)
@Id
private UUID id;
```

**Assigned natural key** — when the id comes from OUTSIDE the application and must
be preserved: a numeric id from an external system being mirrored, an
externally-issued code. NO `@JmixGeneratedValue` — it makes Jmix assign the id at
creation, which is exactly what must not happen here. The writer sets the id:

```java
@Column(name = "ID", nullable = false)
@Id
private Long id;              // assigned from the source system, never generated
```

```java
ExternalAccount account = dataManager.create(ExternalAccount.class);
account.setId(dto.id());       // caller MUST set it — nothing generates it
dataManager.save(account);
```

Pick the assigned key when: the source system's id is the identity users and
integrations refer to, or a sync must upsert by that id (load-by-id, insert if
absent). Pick the generated surrogate otherwise — including when the external key
merely needs to be UNIQUE, which is a unique index on an ordinary column, not an
id. Match the Liquibase column type to the Java type (`bigint` for `Long`, not
`${uuid.type}`).

**Inherited key from a framework `@MappedSuperclass`** — when the entity extends a
base class the framework ships (the application-settings base, and other add-on
base classes), the key is declared there already. Write NO `@Id`, no
`@JmixGeneratedValue` and no `@Version` on the subclass. Read that superclass
before writing the changelog: it decides the id column's name AND type — an `int`
or `bigint` key is common there, so `${uuid.type}` is the wrong default — and it
often contributes further columns (a version, a tenant column) that the table must
still create even though no field in the subclass mentions them.

## Required references — BOTH annotations, or the field is not really required

A required `@ManyToOne` needs the constraint in two places:

```java
@JoinColumn(name = "CUSTOMER_ID", nullable = false)   // ← metamodel: mandatory
@ManyToOne(fetch = FetchType.LAZY, optional = false)  // ← JPA: not optional
private Customer customer;
```

Omitting `nullable = false` on `@JoinColumn` leaves the Jmix metamodel treating
the attribute as OPTIONAL: the UI does not require it, no validation fires, and
the failure surfaces later as a database constraint error — or as a null nobody
expected. `optional = false` alone is not enough. This is a hard rule; it holds
for every required reference, not only composition children.

## Widening an existing entity

Adding fields to an entity that already exists is a DIFFERENT task from creating
one. Change only what the requirement asks for:

- **Do NOT touch** the `@Id` field or its strategy, `@Table(name = ...)`,
  `@Version`, or the existing columns' types and constraints. An entity with an
  assigned `Long` id stays that way — do not "normalize" it to a generated UUID.
- **Add** the new fields with their exact constraints, plus new `@Index` entries in
  `@Table(indexes = {...})` if the new columns need indexing (see below).
- **Pair it with an `addColumn` changelog**, never a second `createTable` — see
  `jmix-create-liquibase-changelog` ("Changing an existing table").
- Follow the precedent already in the project: read the entity's own changelog
  before writing the new one, and match its naming and column-type style.

## Index parity — declare every index in BOTH places

When you add an index (typically on a new reference/FK column), declare it in the
entity annotation AND in the changelog:

```java
@Table(name = "CUSTOMER", indexes = {
        @Index(name = "IDX_CUSTOMER_MANAGER", columnList = "MANAGER_ID"),
        @Index(name = "IDX_CUSTOMER_REGION", columnList = "REGION_ID")
})
```

```xml
<createIndex indexName="IDX_CUSTOMER_MANAGER" tableName="CUSTOMER">
    <column name="MANAGER_ID"/>
</createIndex>
```

Same names on both sides. **No gate catches the drift** — a missing `@Index`
changes neither the metamodel nor the schema, so `compileJava`, the Jmix
inspection, and a green `clean test` all pass with the index in the changelog only.
It breaks Studio round-tripping and the code-as-source-of-truth convention, and it
is found by nothing but this check. Do it as you write the column, not as a later
audit.

### Make it unique when the column is a natural key

For a single-column natural key that must be unique, add `unique = true` to the
`@Index` and mirror it with `unique="true"` on the `<createIndex>`:

```java
@Table(name = "DEPARTMENT", indexes = @Index(
        name = "IDX_DEPARTMENT_ON_NAME", columnList = "NAME", unique = true))
```

```xml
<createIndex indexName="IDX_DEPARTMENT_ON_NAME" tableName="DEPARTMENT" unique="true">
    <column name="NAME"/>
</createIndex>
```

For a rule that spans several columns, use a unique **constraint** rather than a
unique index — `@Table(uniqueConstraints = @UniqueConstraint(name = "UK_ORDER_LINE",
columnNames = {"ORDER_ID", "PRODUCT_ID"}))` paired with `<addUniqueConstraint
tableName="ORDER_LINE" columnNames="ORDER_ID, PRODUCT_ID"
constraintName="UK_ORDER_LINE"/>`. Same name on both sides, as above.

One rule neither can express: a **case-insensitive** uniqueness constraint cannot be
declared with `@Index`/`@UniqueConstraint` at all — it needs a functional index on
`lower(column)` and dialect-specific SQL, so decide it at design time, not when
writing the changelog.

## Required-field defaults — at the ENTITY layer, not elsewhere

When a required field "defaults to X" or is "auto-set on create/update"
(e.g. "default now()", "auto-set", "defaults to 0"), the default MUST
apply at the entity layer so a bare `DataManager.create()` +
`DataManager.save()` succeeds with the caller never touching the field.
This is the contract: programmatic paths (services, REST, tests) bypass
the UI, so a default that lives only in the view is no default at all.

Three patterns, in order of preference:

**Constant default — field initializer**

```java
@Column(name = "QUANTITY", nullable = false)
@NotNull
private Integer quantity = 0;
```

**Initial default at creation — `@PostConstruct`**

`@PostConstruct` fires when Jmix instantiates the entity
(`DataManager.create()`, `Metadata.create()`, `DataContext.create()`),
before the caller sets any field. Works for both JPA entities and DTOs.

```java
@Column(name = "CREATED_AT", nullable = false)
@NotNull
private LocalDateTime createdAt;

@PostConstruct
public void postConstruct() {
    createdAt = LocalDateTime.now();
}
```

Imports (Spring Boot 3 uses `jakarta.*`, never `javax.*`): `@PostConstruct` from `jakarta.annotation`; `@NotNull` / `@Email` from `jakarta.validation.constraints`.

**Auto-set on every save — `EntitySavingEvent` listener** (for
`lastUpdated`-style fields that must touch on UPDATE too, or for any
cross-entity defaulting):

```java
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerSavingListener {

    @EventListener
    public void onSaving(EntitySavingEvent<Customer> event) {
        event.getEntity().setLastUpdated(LocalDateTime.now());
    }
}
```

`EntitySavingEvent` fires before EVERY save (both insert and update),
so the listener covers initial and subsequent timestamps in one place.
`Customer` must declare the `lastUpdated` field; for the full event-listener
pattern see `jmix-add-entity-event-listener`.

### Anti-patterns that look right and break tests

| Wrong placement                                    | Why it fails                                   |
|----------------------------------------------------|------------------------------------------------|
| Default set only in the detail view's `InitEntityEvent` | Non-UI saves bypass the view; `DataManager.save()` hits the `@NotNull` violation. |
| Default set only in a service mutator that runs on UPDATE | The initial INSERT has no default; `@NotNull` fails. And a test calling `DataManager` directly never enters the service. |
| Default set in an `EntityChangedEvent` listener    | The listener fires AFTER persist — too late for `@NotNull`. It reacts to saves; it does not default them. |
| Default set only in the calling UI controller      | Programmatic paths (services, REST, tests) bypass it. |

## Accessors

Match what the project's entities already do. A codebase where every entity carries
`@Getter @Setter` is a working configuration — the enhancer runs over those classes
and the suite passes — so writing the one new entity with hand-written accessors
makes it the odd one out for a breakage that does not occur. In a project with
hand-written accessors, write them by hand.

## Composition Checklist

For parent-child aggregates:

- Parent collection has `@Composition`.
- Parent collection has `@OnDelete(DeletePolicy.CASCADE)` when child lifecycle belongs to parent.
- Child has a non-null back reference to parent.
- Child `@ManyToOne` uses `fetch = FetchType.LAZY` and `optional = false`.
- Child join column is `nullable = false`.
- Parent detail view edits the child collection via a property-bound `<collection property=...>` (no loader/query), and the parent fetchPlan includes the child property.
- The child's own detail view exists, plus its role policy, when the UI opens the child in a dialog.

```java
import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Composition
@OnDelete(DeletePolicy.CASCADE)
@OneToMany(mappedBy = "parent")
private List<ChildLine> lines;  // leave uninitialized — Jmix returns a NotInstantiatedList

@JoinColumn(name = "PARENT_ID", nullable = false)
@ManyToOne(fetch = FetchType.LAZY, optional = false)
private Parent parent;
```

## Auditing and Soft Delete

Add audit fields with the Spring Data annotations from `org.springframework.data.annotation`: `@CreatedBy`, `@CreatedDate`, `@LastModifiedBy`, `@LastModifiedDate`. For soft delete add `@DeletedBy` and `@DeletedDate` from `io.jmix.core.annotation` — soft-deleted rows are then auto-filtered out of `DataManager`/JPQL queries.

## Calculated and Transient Properties

Non-persistent derived attributes use `@JmixProperty` + `@Transient` + `@DependsOnProperties({"a", "b"})` (`@JmixProperty` from `io.jmix.core.metamodel.annotation`). The same applies to an `@InstanceName` method: it must carry `@DependsOnProperties` listing every attribute it reads so they are fetched.

## File attributes

Use `FileRef` for an uploaded file reference. The JPA converter is applied automatically:

```java
import io.jmix.core.FileRef;

@Column(name = "SCAN_FILE", length = 1024)
private FileRef scanFile;
```

Map the matching Liquibase column as `varchar(1024)`.

## Embeddable, Inheritance, and Data Stores

- `@Embeddable` value objects (still annotated `@JmixEntity`) are supported, as are JPA inheritance strategies (`@Inheritance` with `JOINED`, `SINGLE_TABLE`, or `TABLE_PER_CLASS`).
- For `SINGLE_TABLE`, an **abstract** base class is normal and carries NO
  `@DiscriminatorValue` of its own — it is never instantiated, so there is no row
  to discriminate. Only the concrete subclasses need one. (The published examples
  usually show a concrete base that does have one; the difference is plain JPA, not
  a Jmix rule — see `jmix-verify-api-symbol`.)
- **Loading through the base class does not fetch subclass attributes.** A
  `dataManager.load(BaseType.class)` builds its default fetch plan from the base
  metaclass's own properties, so casting the result to a subclass and reading a
  subclass-declared attribute throws `IllegalStateException: Cannot get unfetched
  attribute`. See `jmix-configure-fetch-plan` for the fix.
- For a non-default data store, annotate the entity with `@Store(name = "...")` (defined in `application.properties`); add-on entities use an entity-name prefix, e.g. `@Entity(name = "app_Customer")`.

## Constraint Audit

`compileJava` does not build the schema — Liquibase does — so Java/DDL
drift is silent. Check Java annotations and the Liquibase changelog side
by side:

- `nullable` / `@NotNull`
- `length`
- `precision` and `scale`
- enum id values and column type
- foreign key nullability
- indexes and unique constraints
- default values for required fields

## Semantic Constraint Checks

Apply common Java validation and persistence mappings when the field semantics are clear:

- Fields named `email` should usually have `@Email` unless the requirements explicitly say otherwise.
- Unlimited or large text should use the project pattern for long text, usually `@Lob` plus a matching Liquibase type, not an invented arbitrary length.
- `BigDecimal` columns must use the exact required precision and scale in both Java and Liquibase.
- Do not invent a length for a field when the requirements say it is unlimited or when the existing project uses long text for the same concept.

## Forbidden

- Missing `@JmixEntity`.
- Constructor-based entity creation.
- `@Data`, `@EqualsAndHashCode` or `@ToString` on a Jmix entity: they generate `equals`/`hashCode`/`toString` over mutable persistent fields, replacing the identity semantics the framework relies on and touching lazy references while doing it.
- `FetchType.EAGER`.
- Missing Liquibase changelog for persistent changes.
- A required `@ManyToOne` with `optional = false` but no `@JoinColumn(nullable = false)` — the metamodel then treats it as optional and the UI does not require it.
- `@JmixGeneratedValue` on an assigned natural key — Jmix then assigns the id itself, defeating the point of preserving the source system's id.
- Changing an existing entity's id strategy, `@Table` name, or `@Version` while adding fields to it.
- An index declared in the changelog but missing from `@Table(indexes = …)`, or the reverse.
- Nullable child back references in composition aggregates.
- Relying only on UI initialization for required persistence fields.
- Instantiating or replacing a collection field that Jmix populated — it may be a `NotInstantiatedList`/`NotInstantiatedSet`. Leave collection fields uninitialized; do not assign `new ArrayList`/`new HashSet`.
- A 0-byte `.java` — it passes compile and the clean test boot, but breaks the registry. Confirm every file you wrote is non-empty.
