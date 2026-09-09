---
name: jmix-create-enum
description: Create a Jmix enum for entity attributes with stable database ids and localization.
---

# Create Jmix Enum

Use this skill when an entity attribute has a fixed set of values.

## Steps

0. **Look for an existing enum convention in the project first.** Many codebases
   have a shared base interface that every enum implements, often with a default
   `getId()` — see "Joining an existing enum family" below. Match it instead of
   introducing a second scheme.
1. Create the enum in the `entity` package.
2. Implement `io.jmix.core.metamodel.datatype.EnumClass<T>`.
3. Use stable database ids, not display labels.
4. Add a typed `fromId()` method annotated with `@Nullable`.
5. Store the enum id type in the entity field.
6. Add getter/setter conversion in the entity.
7. Add Liquibase column matching the id type.
8. Add enum message keys in all locale files — see `jmix-add-i18n-keys` for the `<package>/<EnumClass>.<CONSTANT>` key shape (e.g. `com.company.app.entity/TransactionType.INCOME`), NOT an all-dots `FQCN.CONSTANT` form.

## Enum Template

```java
import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum TransactionType implements EnumClass<String> {
    INCOME("INCOME"),
    OUTCOME("OUTCOME");

    private final String id;

    TransactionType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static TransactionType fromId(String id) {
        for (TransactionType value : TransactionType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
```

## Entity Mapping

```java
@Column(name = "TYPE", nullable = false, length = 50)
private String type;

public TransactionType getType() {
    return type == null ? null : TransactionType.fromId(type);
}

public void setType(TransactionType type) {
    this.type = type == null ? null : type.getId();
}
```

## Stable-id type-chain

Keep one consistent id type across `EnumClass<T>`, `getId()`/`fromId()`, the entity field, and the Liquibase column (e.g. all `String`/`VARCHAR`). A mismatch compiles cleanly but silently corrupts load/save. Each literal id must be a hardcoded stable value, never a display label or `ordinal()`/`name()`.

When binding an enum attribute in a view, use `<comboBox>` (its `Range` is an enumeration) — not `entityComboBox`, which is for entity associations.

## Joining an existing enum family

Before writing the template below, grep for a base interface the project's enums
already implement:

```bash
grep -rn "implements .*EnumClass" src/main/java --include='*.java' | head
```

If one exists and its `getId()` returns `name()`, **match it**. The `.name()`
prohibition below is about not introducing that scheme into a project that has no
id convention yet — it is not a reason to make one new enum the only member of a
family with a different id scheme.

## Forbidden

- `io.jmix.core.EnumClass`.
- Raw `EnumClass` without a type parameter.
- Storing display labels as ids.
- `ordinal()` persistence, or introducing `.name()` ids into a project that has no enum id convention yet (an established project-wide convention is matched, not split — see above).
- Missing enum message keys.
