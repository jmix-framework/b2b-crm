---
name: jmix-add-entity-event-listener
description: Add Jmix entity lifecycle event listeners to perform additional actions with saved or loaded entities.
---

# Add Entity Event Listener

Use this skill when business logic must react to entity save, change, delete, or load events.

## Steps

1. Create a Spring `@Component` listener in `service` or `listener`.
2. Use `org.springframework.context.event.EventListener`.
3. Use exact Jmix imports:
   - `io.jmix.core.event.EntityChangedEvent`
   - `io.jmix.core.event.EntityLoadingEvent`
   - `io.jmix.core.event.EntitySavingEvent`
4. For created entities, load by `event.getEntityId()` when related data is needed.
5. If you specify a custom fetch plan, include every scalar and reference property read later (see `jmix-configure-fetch-plan`).
6. For deleted entities, do not load `event.getEntityId()`; use old values or old reference ids from `event.getChanges()`.
7. Use `EntitySavingEvent` for defaults or transformations that must happen before data is saved; a required persistent default still belongs at the entity layer (see `jmix-create-entity`).
8. Use `EntityLoadingEvent` for initializing non-persistent attributes from already loaded local persistent state.
9. Use a before-commit `@EventListener` path for validation that must reject the current save/remove operation.
10. Put multi-entity changes in a transactional service method when atomicity matters.
11. Reject unsupported updates/deletes inside the event path before treating work as complete.
12. Search the changed code for `@TransactionalEventListener`; if the listener performs validation, rejects updates/deletes, sets required defaults, or performs required synchronous side effects, replace it with `@EventListener` plus `EntitySavingEvent`/`EntityChangedEvent` or another before-commit path.
13. Add tests or at least compile/startup validation for the event listener.

## EntityChangedEvent

### Listener Template

```java
import io.jmix.core.DataManager;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntityLoadingEvent;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LedgerEntryEventListener {

    private final DataManager dataManager;
    private final LedgerService ledgerService;

    public LedgerEntryEventListener(DataManager dataManager,
                                    LedgerService ledgerService) {
        this.dataManager = dataManager;
        this.ledgerService = ledgerService;
    }

    @EventListener
    public void onLedgerEntryChanged(EntityChangedEvent<LedgerEntry> event) {
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            LedgerEntry entry = dataManager.load(event.getEntityId()).one();
            ledgerService.applyEntry(entry);
            return;
        }

        throw new UnsupportedOperationException("This record cannot be updated or deleted");
    }

    @EventListener
    public void onLedgerEntrySaving(EntitySavingEvent<LedgerEntry> event) {
        if (event.getEntity().getCreatedDate() == null) {
            event.getEntity().setCreatedDate(LocalDateTime.now());
        }
    }

    @EventListener
    public void onLedgerEntryLoading(EntityLoadingEvent<LedgerEntry> event) {
        LedgerEntry entry = event.getEntity();
        entry.setDisplayLabel(entry.getNumber() + " / " + entry.getType());
    }
}
```

### Fetch Plan Safety

For non-deleted events, the loaded entity should contain every property the listener reads. The safest default is loading by event id with the normal plan:

```java
LedgerEntry entry = dataManager.load(event.getEntityId()).one();
```

For non-deleted events, if you use a custom fetch plan, add all accessed scalar fields and references:

```java
LedgerEntry entry = dataManager.load(LedgerEntry.class)
        .id(event.getEntityId())
        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                .add("account", FetchPlan.BASE))
        .one();

ledgerService.apply(entry.getAccount().getId(), entry.getAmount(), entry.getType());
```

After writing the listener, scan the method: every `entry.getX()` used after loading must be available in the fetch plan.

### Event Timing

Use normal Spring `@EventListener` for logic that must affect the current save/remove operation:

- required default values;
- rejecting unsupported updates or deletes;
- synchronous changes to related persistent state;
- validation whose exception must propagate to `DataManager.save()` or `DataManager.remove()`.

`@TransactionalEventListener` is for after-transaction reactions such as notifications or integration events. Do not use it when failure must roll back or reject the current persistence operation.

`EntityChangedEvent` (before commit) is delivered AFTER the SQL flush. If the write
being validated can itself violate a database constraint, the constraint error is
raised first and the listener never runs.

Use `EntityChangedEvent.Type.DELETED` to detect deletes. Deleted entity instances cannot be loaded by `event.getEntityId()` because they have already been removed, so delete-side logic must use the old values and old reference ids available from `event.getChanges()`.

For `Type.DELETED`, `event.getChanges()` snapshots every non-read-only property of the entity with its last-known pre-delete value — unlike `Type.UPDATED`, where only actually-mutated attributes appear. So `getOldValue(name)` works for scalar (non-reference) attributes too, and references come back as ids:

```java
@EventListener
public void onOrderLineChanged(EntityChangedEvent<OrderLine> event) {
    if (event.getType() == EntityChangedEvent.Type.DELETED) {
        LocalDate postingDate = event.getChanges().getOldValue("postingDate"); // scalar pre-delete value
        Id<Order> orderId = event.getChanges().getOldReferenceId("order");     // reference as id
        // validate against the pre-delete state, e.g. reject the delete
    }
}
```

## EntitySavingEvent and EntityLoadingEvent

`EntitySavingEvent` contains the entity instance before it is written to the data store. Use it for required defaults, value normalization, and transformations that must be persisted with the current save operation.

`EntityLoadingEvent` contains the loaded entity instance after it is read from the data store. Use it to initialize non-persistent attributes from local persistent fields, for example decrypting a stored value into a transient UI-facing property.

For `EntitySavingEvent` and `EntityLoadingEvent`, read and write only local attributes of the event entity. Do not assume referenced entities are loaded or that loading references inside an `EntityLoadingEvent` will cascade loading events predictably.

### Reading a reference you nevertheless need

Inside `EntitySavingEvent` an unloaded reference does NOT lazy-load — the getter
throws. That is an exception to the ordinary rule (outside the save path a reference
missing from the fetch plan is lazy-loaded and only local attributes throw), so code
written from the general rule fails here.

This also rules out the obvious workaround: `dataManager.load(Category.class)
.id(order.getCategory().getId())` never runs, because `order.getCategory()` throws
first. `entity.getRef().getId()` is not a safe way to learn a reference's id — it
needs the reference loaded.

```java
@EventListener
public void onOrderSaving(EntitySavingEvent<Order> event) {
    Order order = event.getEntity();
    Category category = entityStates.isLoaded(order, "category")
            ? order.getCategory()
            : dataManager.load(Order.class).id(order.getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("category", FetchPlan.BASE))
                    .optional().map(Order::getCategory).orElse(null);
    // ... use category, keep working with `order`
}
```

Read the reference OUT of the separately loaded instance and keep using the event's
entity. Do NOT continue with the reloaded copy and do not merge it back: it carries
what is in the database and overwrites values set in memory but not yet saved.

## Forbidden

- Wrong event import packages such as `io.jmix.core.entity.EntityChangedEvent`; use `io.jmix.core.event.EntityChangedEvent`.
- Assuming `EntityChangedEvent` directly contains the full entity instance.
- Assuming `EntityLoadingEvent` is a replacement for fetching references or running cross-entity queries.
- Reading an entity property that is omitted from a custom fetch plan.
- `@TransactionalEventListener` for validation, required synchronous side effects, or immutable-record enforcement.
- Putting UI code in entity listeners.
- Side effects without an explicit service method when several entities must stay consistent.
