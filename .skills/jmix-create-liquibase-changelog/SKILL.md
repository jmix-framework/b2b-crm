---
name: jmix-create-liquibase-changelog
description: Create Liquibase changelogs that exactly match Jmix entity model changes, and register database changelogs supplied by Jmix add-ons that own persistent entities.
---

# Create Liquibase Changelog

Use this skill for every persistent entity or schema change, including adding a
Jmix add-on that owns database tables.

## Steps

1. Find the root changelog path from `application.properties`.
2. Follow the existing naming style: sequential files or date/time folders.
3. Create a new changelog file for the schema change.
4. Make sure it is reachable from the root `changelog.xml`: usually by placing it under the directory covered by the project's `<includeAll>`, or by adding an explicit `<include>` only when the project uses explicit includes.
5. Check whether the project also keeps a **baseline** changelog — one that creates the whole schema for a fresh database. If it does, mirror the change there too. See "Baseline changelogs" below.
6. When adding a Jmix add-on that owns persistent entities, add its module changelog to the root changelog with an explicit `<include>`.
7. Use a changeset id and author that match project style; do not reuse an id within the same changelog file.
8. Add `ID` and `VERSION` columns for standard Jmix entities.
9. Add every persistent entity field with exact type, length, precision, scale, and nullability.
10. Add foreign keys for references.
11. Add indexes and unique constraints required by the entity or domain.
12. Verify the table and column names match Java annotations.
13. Use only type macros already present in the project. If no macro exists, use the standard Liquibase type.

## Standard Types

| Java type | Liquibase type |
| --- | --- |
| UUID | `${uuid.type}` |
| String | `varchar(n)` |
| Integer | `int` |
| Long | `bigint` |
| BigDecimal | `decimal(p,s)` |
| Boolean | `boolean` |
| LocalDate | `date` |
| LocalDateTime | `timestamp` |
| OffsetDateTime / ZonedDateTime | `timestamp with time zone` |
| `@Lob` String (unlimited text) | `clob` |
| `@Lob` byte[] | `blob` |
| FileRef | `varchar(1024)` |
| Enum id string | `varchar(50)` |

An entity with an **assigned natural key** takes the id column type of its Java
field — `bigint` for a `Long` id, not `${uuid.type}`. See `jmix-create-entity`
(Id strategy).

An entity extending a framework `@MappedSuperclass` inherits its key, and usually
a version and a tenant column with it. No field in the subclass names them, but
the table must still create them: read the superclass and take each inherited
column's name and type from there — do not assume `${uuid.type}` for the primary
key, these base classes are commonly keyed by an `int`.

## Entity Table Skeleton

```xml
<changeSet id="create-customer" author="app">
    <createTable tableName="CUSTOMER">
        <column name="ID" type="${uuid.type}">
            <constraints nullable="false" primaryKey="true" primaryKeyName="PK_CUSTOMER"/>
        </column>
        <column name="VERSION" type="int">
            <constraints nullable="false"/>
        </column>
        <column name="NAME" type="varchar(100)">
            <constraints nullable="false"/>
        </column>
    </createTable>
</changeSet>
```

## Changing an existing table — `addColumn`, never a second `createTable`

Widening an entity that already exists (see `jmix-create-entity`, "Widening an
existing entity") means a NEW changelog file with `addColumn`. Do not edit the
original `createTable` changeset — it is already applied, and changing it breaks
the checksum and hard-fails startup.

```xml
<changeSet id="1" author="app">
    <addColumn tableName="CUSTOMER">
        <column name="NOTES" type="clob"/>
        <column name="LAST_CONTACTED_AT" type="timestamp with time zone"/>
        <column name="MANAGER_ID" type="${uuid.type}"/>
    </addColumn>
</changeSet>

<!-- FK and index for the new reference column: separate changesets -->
<changeSet id="2" author="app">
    <addForeignKeyConstraint baseTableName="CUSTOMER" baseColumnNames="MANAGER_ID"
                             referencedTableName="EMPLOYEE" referencedColumnNames="ID"
                             constraintName="FK_CUSTOMER_ON_MANAGER"/>
    <createIndex indexName="IDX_CUSTOMER_MANAGER" tableName="CUSTOMER">
        <column name="MANAGER_ID"/>
    </createIndex>
</changeSet>
```

Rules for a widening changelog:

- A column added to a table that already has rows CANNOT be `nullable="false"`
  without a default or a data fix — existing rows have no value. Add it nullable,
  or add it with a `defaultValue`, or backfill with `<update>` and then tighten.
- The column type comes from the Java field, see Standard Types above.
- Read the table's ORIGINAL changelog first and match its naming and type style.

## Index parity — the changelog is only half of it

Every index in a changelog must also exist as an `@Index` in the entity's
`@Table(indexes = {...})`, with the SAME name — and the reverse. Nothing enforces
this: `compileJava`, the Jmix inspection, and a green `clean test` all pass when
the index exists only in the changelog. Write both sides in the same edit, while
you still remember the column. See `jmix-create-entity` (Index parity).

## One-time data fixes — `<update>`, and how to write NULL

A schema change sometimes needs a matching data fix (backfilling a new column,
clearing a timestamp so a job reprocesses every row). That is an ordinary
`<update>` changeset.

```xml
<!-- literal value -->
<changeSet id="3" author="app">
    <update tableName="CUSTOMER">
        <column name="STATUS" value="PENDING"/>
    </update>
</changeSet>

<!-- set to NULL: a <column> with NO value attribute at all -->
<changeSet id="4" author="app">
    <update tableName="CUSTOMER">
        <column name="LAST_CONTACTED_AT"/>
        <column name="LAST_INVOICED_AT"/>
    </update>
</changeSet>
```

`value=""` is NOT null — on most databases it writes an empty string, and on a
non-text column it fails. To write NULL, omit every `value`/`valueXxx` attribute.
Add a `<where>` clause when the fix targets a subset of rows; without one it
updates every row, which is usually what a one-time reset wants.

## Audit and soft-delete columns

If the entity carries audit (`@CreatedBy` / `@CreatedDate` / `@LastModifiedBy` /
`@LastModifiedDate`) or soft-delete (`@DeletedBy` / `@DeletedDate`) annotations,
add the matching columns INSIDE its `<createTable>`. They are set by Jmix at
runtime, so keep them NULLABLE (no `nullable="false"`). Add only the columns
whose annotations are actually on the entity — see `jmix-create-entity`
(Auditing and Soft Delete).

```xml
<column name="CREATED_BY" type="varchar(255)"/>
<column name="CREATED_DATE" type="timestamp"/>
<column name="LAST_MODIFIED_BY" type="varchar(255)"/>
<column name="LAST_MODIFIED_DATE" type="timestamp"/>
<column name="DELETED_BY" type="varchar(255)"/>
<column name="DELETED_DATE" type="timestamp"/>
```

## Parent → child ordering (FK references must follow the parent table)

When a child table has a foreign key to a parent, the parent `createTable`
MUST come BEFORE the child's `createTable` / `addForeignKeyConstraint`. A
changeSet that references a table not yet created fails at startup and takes
down the whole context — including tests that only touch the data model.
Order the parent first, the child (with its FK) second:

```xml
<!-- parent FIRST -->
<changeSet id="create-parent" author="app">
    <createTable tableName="PARENT">
        <column name="ID" type="${uuid.type}">
            <constraints nullable="false" primaryKey="true" primaryKeyName="PK_PARENT"/>
        </column>
        <column name="VERSION" type="int"><constraints nullable="false"/></column>
        <column name="NAME" type="varchar(100)"><constraints nullable="false"/></column>
    </createTable>
</changeSet>

<!-- child SECOND: its FK references the already-created parent -->
<changeSet id="create-child" author="app">
    <createTable tableName="CHILD">
        <column name="ID" type="${uuid.type}">
            <constraints nullable="false" primaryKey="true" primaryKeyName="PK_CHILD"/>
        </column>
        <column name="VERSION" type="int"><constraints nullable="false"/></column>
        <column name="NAME" type="varchar(100)"><constraints nullable="false"/></column>
        <column name="PARENT_ID" type="${uuid.type}"><constraints nullable="false"/></column>
    </createTable>
    <addForeignKeyConstraint baseTableName="CHILD" baseColumnNames="PARENT_ID"
                             referencedTableName="PARENT" referencedColumnNames="ID"
                             constraintName="FK_CHILD_ON_PARENT"/>
</changeSet>
```

For a **composition** child, the delete cascade is enforced by Jmix at the
application layer (`@Composition` + `@OnDelete(DeletePolicy.CASCADE)` on the
entity), NOT by the database — Jmix uses soft delete by default, so a DB-level
`onDelete="CASCADE"` would never fire. Leave the FK without `onDelete` unless
you specifically need hard-delete DB-level enforcement.

## Root Changelog Reachability

```xml
<includeAll path="/com/company/app/liquibase/changelog"/>
```

If the project uses explicit includes instead of `includeAll`, follow that existing style:

```xml
<include file="/com/company/app/liquibase/changelog/030-customer.xml"/>
```

## Baseline changelogs — the dated file is not always the whole job

Some projects keep a BASELINE alongside the dated migrations: one changelog that
creates every table for a fresh database, and usually a second one that adds every
FK and index. A fresh database builds the schema from the baseline and MARK_RANs the
dated migrations through their own preconditions, instead of replaying years of
history.

Read the root `changelog.xml` and look at what else it includes. Two signs of a
baseline: it creates tables that predate the current release, and its changesets carry
`<validCheckSum>ANY</validCheckSum>`. That marker is there so the baseline can be
APPENDED TO in place — it is the one exception to "never edit an applied changeset".

When a project has one, a schema change lands in both places, in the same commit:

- the dated migration — for databases that already exist;
- the baseline — put the `createTable` (or a new `<column>` on the table's existing
  `createTable`) INSIDE the existing changeset, next to related tables; add the FK and
  each index as a NEW changeset in the constraints changelog, following that file's own
  pattern.

Keep every name identical across the dated file, the baseline and the entity's
`@Table(indexes = ...)`.

## Jmix add-on changelogs

Adding an add-on dependency does not make its module changelog reachable from a
project master changelog that lists module changelogs explicitly. When the add-on
owns persistent entities, find its changelog resource and add an explicit include
to the root `changelog.xml`. For example, the Audit add-on requires:

```xml
<include file="/io/jmix/audit/liquibase/changelog.xml"/>
```

Jmix module changelogs commonly use this path pattern:

```xml
<include file="/io/jmix/<addon>/liquibase/changelog.xml"/>
```

Confirm the actual resource path in the add-on before adding it. Do not assume
that the application's `<includeAll>` covers changelogs packaged in add-on JARs.

After startup, verify that the add-on tables exist and exercise a feature that
uses them. Also search the complete startup log for missing-schema errors:

```bash
rg -n 'does not exist|relation .* does not exist' /path/to/startup.log
```

Treat every match as a failure until it is explained. A green `clean test` proves
that the context loads, but it does not prove the add-on schema was created when
no test accesses its tables. Some add-ons log the missing-table error and allow
the application to continue running with the feature unavailable.

## Forbidden

- New changelog file that is not reachable from the root changelog.
- Reusing a changeset id in the same changelog file.
- Modifying a changeset already applied to a DB: it changes the checksum and Liquibase hard-fails at startup. Add a NEW changeset instead.
- Raw `UUID` type instead of `${uuid.type}`.
- Invented type macros such as `${datetime.type}` when the project does not define them.
- Missing `VERSION`.
- Nullable database column for a required Java field.
- Java precision/length different from Liquibase precision/length.
- Missing FK for persistent references.
- A child table / FK changeSet ordered BEFORE the parent table it references.
- A second `createTable` for a table that already exists, when the change is new columns (use `addColumn`).
- `nullable="false"` on a column added to a populated table without a default or a backfill.
- `value=""` where NULL is meant — omit the value attribute instead.
- An index in the changelog with no matching `@Index` in the entity's `@Table`.
- A Jmix add-on with persistent entities whose module changelog is not explicitly
  reachable from the root changelog.
- A dated migration with no matching entry in the project's baseline changelogs, when
  the project keeps one.
