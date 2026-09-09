---
name: jmix-configure-fetch-plan
description: Configure or audit Jmix fetch plans in XML views, fragments, DataManager loads, repositories, and entity events when loading references, avoiding N+1 queries, fixing unfetched attribute errors, or tuning data loading.
---

# Configure Fetch Plan

Use this skill when a task changes what entity attributes or references are loaded.

## Steps

1. Identify every property read by the view, service, listener, renderer, mapper, or assertion.
2. Start with `_base` unless there is a measured reason to load a partial entity.
3. Add reference properties explicitly when a loaded entity is detached or when a list/grid displays reference attributes.
4. For list views, include only references and scalar columns that are displayed or used by renderers/actions.
5. For detail views and compositions, include edited reference properties and child collections that the form or grid uses.
6. For service/listener code, add a fluent `DataManager.fetchPlan(...)` or named plan before reading references after load.
7. Avoid deep nested collections; prefer a second focused load when a graph becomes wide or multi-collection.
8. Check custom fetch plans against every `getX()` call after load.
9. Verify property names and run the load path before trusting the plan (see **Verify** below).

## XML Pattern

```xml
<collection id="ordersDc" class="com.company.app.entity.Order">
    <fetchPlan extends="_base">
        <property name="customer" fetchPlan="_instance_name"/>
        <property name="lines" fetchPlan="_base"/>
    </fetchPlan>
    <loader id="ordersDl" readOnly="true">
        <query><![CDATA[select e from Order e]]></query>
    </loader>
</collection>
```

Use the JPA/Jmix entity name in JPQL, not the database table name.

## DataManager Pattern

```java
List<Order> orders = dataManager.load(Order.class)
        .query("select e from Order e")
        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                .add("customer", FetchPlan.INSTANCE_NAME))
        .list();
```

For an event listener that reads a reference:

```java
Order order = dataManager.load(event.getEntityId())
        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                .add("customer", FetchPlan.BASE))
        .one();
```

### A reference property needs the TWO-argument `.add(...)`

```java
.add("customer", FetchPlan.BASE)   // RIGHT: nested plan for the reference
.add("customer")                   // WRONG for a reference: EMPTY nested plan
```

The one-argument `.add("customer")` compiles, runs, and loads the reference —
with an **empty** nested fetch plan. Nothing fails at load time; the failure comes
later, the first time anything reads an attribute of `customer`:

```
IllegalStateException: Cannot get unfetched attribute [name] from detached object
```

This is easy to miss because the one-argument form is perfectly correct for a
**scalar** property (`.add("orderDate")`). Only references need the second
argument: `FetchPlan.INSTANCE_NAME` when a grid or caption shows the reference,
`FetchPlan.BASE` when code reads its other attributes.

### A dotted path `add("a.b", PLAN)` leaves `a` without its own attributes

```java
.add("product.supplier", FetchPlan.BASE)          // WRONG if you also read product's own fields
.add("product", p -> p.addFetchPlan(FetchPlan.BASE)
        .add("supplier", FetchPlan.BASE))         // RIGHT
```

A dotted path pulls the intermediate reference in only as a HOLDER for the nested
attribute. `product` enters the plan for the sake of `supplier`, and its own
attributes are NOT loaded — so the first read of `product.getName()` throws the same
`Cannot get unfetched attribute` as the one-argument form above. Read it as
"product at a minimum, plus its supplier", not "product in full, plus its supplier".

### Single-table inheritance — a base-class load does not fetch subclass attributes

Given an abstract `Payment` with concrete subclasses `CardPayment` and
`BankTransfer` in one table: `dataManager.load(Payment.class)` builds its default
`_base` plan from the **requested** metaclass's own properties only. It never
includes attributes declared by subclasses, even though the row that comes back IS
the subclass — the generated SQL selects base columns. So this throws:

```java
Payment loaded = dataManager.load(Payment.class).id(id).one();
String authCode = ((CardPayment) loaded).getAuthCode();
// IllegalStateException: Cannot get unfetched attribute [authCode] ... [detached]
```

A plain cast-and-read is never enough. Two fixes:

```java
// (a) load through the CONCRETE subclass — its default plan has the subclass's
//     own attributes, so getAuthCode() is fetched
CardPayment payment = dataManager.load(CardPayment.class).id(id).one();
String authCode = payment.getAuthCode();

// (b) still polymorphic, but the follow-up read goes through a plan built
//     against the CONCRETE class — name the subclass attributes explicitly
CardPayment payment = dataManager.load(CardPayment.class)
        .id(baseTyped.getId())
        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("authCode"))
        .one();
```

The same applies to a **reference** typed as the base class: reading a
subclass-only attribute off it needs a re-load through the concrete subclass, as
in (b). Nothing catches this before runtime — the line that would fail is exactly
the kind of code a test must run.

## Fetch Modes

Set per-property fetch mode to control how references are loaded:

- `AUTO` — framework picks the optimal mode (default).
- `JOIN` — loads the reference in the same SQL query; best for to-one references.
- `BATCH` — loads references in a separate `IN`-clause query; best for to-many collections (avoids N+1).
- `UNDEFINED` — separate SELECT per reference attribute.

Set it with the `fetch` attribute on a fetch-plan property (the XML attribute is `fetch`, NOT `fetchMode`):

```xml
<fetchPlan extends="_base">
    <property name="customer" fetch="JOIN"/>  <!-- to-one -->
    <property name="lines" fetch="BATCH"/>     <!-- to-many collection -->
</fetchPlan>
```

### `BATCH` may do nothing for a NESTED collection — measure, do not assume

A plan built with `FetchPlanBuilder` is installed as a `LOAD_GROUP`, not a `FETCH_GROUP`, because its `loadPartialEntities()` is false. The mode is still recorded — the DEBUG log of `io.jmix.eclipselink.impl.FetchGroupManager` prints `Fetch modes for ...: e.orderLines=BATCH` — but EclipseLink loads such a collection with one query per parent anyway:

```
... WHERE ORDER_ID = ?        -- repeated once for every row of the page
```

Calling `partial()` does not change it. So `fetch="BATCH"` is not a reliable cure for N+1 on a nested collection. When a measurement shows the per-parent queries, take the collection OUT of the fetch plan and load it for the whole page with one repository query instead:

```java
// one query for the page, not one per parent
List<OrderLine> lines = orderLineRepository.findByOrderIdIn(orderIds);
```

Only a count of executed SQL proves which shape you got: `compileJava`, the IDE inspection, and a green `clean test` all pass either way. A test that asserts the query count does not grow with page size is the guard.

Never set `FetchMode.JOIN` on a to-one nested INSIDE a `BATCH` collection. It looks like a way to fold that reference into the collection query, but EclipseLink throws at query time:

```
NullPointerException: Cannot invoke "java.util.Collection.toArray()" because "c" is null
```

## JmixDataRepository

Select the plan in one of two ways: pass a `FetchPlan` as the **last** method argument, or annotate the method with `@FetchPlan("name")` (`io.jmix.core.repository.FetchPlan`). A plain `String` parameter is bound as an ordinary query parameter, NOT a plan selector. Build complex plans with the `FetchPlans` bean: `fetchPlans.builder(Order.class).addFetchPlan(FetchPlan.BASE).add("customer", FetchPlan.INSTANCE_NAME).build()`.

## What a missing attribute does depends on its KIND

| missing from the plan | behaviour |
| --- | --- |
| a local (scalar) attribute | `IllegalStateException: Cannot get unfetched attribute` — no rescue |
| a reference, ordinary code | lazy-loaded: a silent extra SELECT per row, and the reference arrives with all its local attributes |
| a reference, inside `EntitySavingEvent` | `IllegalStateException` — lazy loading is not available there |

## Partial Entity Audit

Use a partial fetch plan only when it is intentionally narrower than `_base`:

- The loaded entity is wide or the result list is large.
- Every local property read later is listed in the plan.
- UI components, renderers, validators, and mappers do not access omitted attributes.
- Tests cover the path that previously caused the performance issue or unfetched attribute error.

## Shared Plans

Prefer inline XML or fluent `DataManager` fetch plans for feature-local needs.

Use `fetch-plans.xml` only when the same complex graph is reused in multiple places. If you add a shared plan, configure or verify the project's `jmix.core.fetch-plans-config` property and keep the name stable.

## Verify — compile is blind to fetch plans

`compileJava` never reads view XML or fetch-plan property names, so a missing
or misspelled reference is invisible until the load path runs: a detached
entity throws `IllegalStateException: Cannot get unfetched attribute [...]` at
RENDER time, or when a service/test reads `getX()` after load. The gate is
never the compiler.

1. **Property names — verify before you type them.** Every `<property
   name="...">` in a `<fetchPlan>` and every `.add("...")` in a fluent plan
   must be a real attribute of the entity, and the `FetchPlan` constants
   (`FetchPlan.BASE`, `FetchPlan.INSTANCE_NAME`) and built-in plan names
   (`_base`, `_instance_name`) must be spelled exactly. Confirm against the
   entity source, Context7 (`/jmix-framework/jmix-context7`), or IDE symbol
   search — see `jmix-verify-api-symbol`.
2. **Static inspection (Gate 1).** Run `jmix-ide-static-analysis`
   (get_file_problems) on the view/fragment XML — the Jmix-XSD-aware
   inspection flags an invalid property path inside a `<fetchPlan>` that the
   compiler ignores.
3. **Run the load path (Gate 2).** Exercise the smallest test or view that
   reads the references; only this catches an attribute that is mapped but
   never fetched.

## Forbidden

- `FetchType.EAGER` to solve loading problems.
- The one-argument `.add("reference")` for a reference property — it builds an empty nested plan.
- Casting a base-class-typed load to a subclass and reading a subclass-only attribute.
- Reading local attributes omitted from a partial fetch plan.
- Loading references inside loops when a fetch plan can load them with the root query.
- `FetchMode.JOIN` on a to-one nested inside a `FetchMode.BATCH` collection — EclipseLink throws at query time.
- Trusting `fetch="BATCH"` to remove N+1 on a nested collection without counting the executed SQL.
- Deep multi-collection graphs in a single list load.
- Using fetch plans as a security boundary.
- `@Table` names in JPQL queries.
- Shared named fetch plans for one-off local view needs.
