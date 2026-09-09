---
name: jmix-add-dialog-detail-flow
description: Open a Jmix entity detail view from a button/action and refresh related data after save.
---

# Add Dialog Detail Flow

Use this skill when a button or action should create or edit an entity in a modal detail view, or collect a small scalar value through a Jmix input dialog. For parent-owned children edited inside the parent's own detail view via a property-bound `<collection property=...>` container (no query loader), use `jmix-create-composition-detail-view` instead.

## Steps

1. Inject `DialogWindows` with `@Autowired`.
2. Inject selected grid, loaders, and any child containers that must be cleared with `@ViewComponent`.
3. On click/action, get the selected master entity if needed.
4. Open `DialogWindows.detail(this, Entity.class).newEntity()` or `.editEntity(entity)`.
5. Use `.withInitializer(...)` to set parent/default fields.
6. Use `.withAfterCloseListener(...)` and refresh every affected loader only after `StandardOutcome.SAVE`.
7. Make sure the opened detail view exists and roles have its `@ViewPolicy` (see `jmix-create-detail-view` and `jmix-create-resource-role`).
8. Use valid listener signatures when installing grid selection listeners.

## Create Child Entity From Selected Master

```java
@Autowired
private DialogWindows dialogWindows;

@ViewComponent
private DataGrid<Order> ordersDataGrid;

@ViewComponent
private CollectionLoader<Order> ordersDl;

@ViewComponent
private CollectionContainer<OrderLine> orderLinesDc;

@ViewComponent
private CollectionLoader<OrderLine> orderLinesDl;

@Subscribe("createLineButton")
public void onCreateLineButtonClick(final ClickEvent<JmixButton> event) {
    Order order = ordersDataGrid.getSingleSelectedItem();
    if (order == null) {
        return;
    }

    dialogWindows.detail(this, OrderLine.class)
            .newEntity()
            .withInitializer(line -> line.setOrder(order))
            .withAfterCloseListener(closeEvent -> {
                if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                    ordersDl.load();
                    orderLinesDl.load();
                }
            })
            .open();
}
```

## Related Grid Loader

Prefer binding to the selected master container when possible:

```xml
<loader id="orderLinesDl" readOnly="true">
    <query><![CDATA[
        select e from OrderLine e
        where e.order = :container_ordersDc
        order by e.createdDate desc
    ]]></query>
</loader>
```

A `:container_<DcId>` parameter is auto-bound and auto-safe — it may sit under `<dataLoadCoordinator auto="true"/>`. A MANUAL parameter (anything not `:container_*` / `:component_*`, e.g. `:param`) is NOT: if it runs with the param unset — left under `auto="true"`, or `load()` called before `setParameter` — it throws an `IllegalStateException` at view open about the missing query parameter. Such a loader MUST be kept OUT of the coordinator and loaded explicitly from the selection handler:

```xml
<facets>
    <!-- coordinator lists only auto-safe loaders; the manual-param loader is NOT here -->
    <dataLoadCoordinator auto="true"/>
</facets>
```

If passing an entity manually, compare the entity-valued property to the entity parameter:

```xml
where e.order = :param
```

If passing only an id manually, compare to the id field:

```xml
where e.order.id = :paramId
```

When a loader query has a required manual parameter, never call `load()` after removing that parameter. Either bind to the selected master container, skip loading until a master is selected, or clear the child container when no master is selected.

```java
Order order = ordersDataGrid.getSingleSelectedItem();
if (order == null) {
    orderLinesDc.getMutableItems().clear();
    return;
}

orderLinesDl.setParameter("order", order);
orderLinesDl.load();
```

## Grid Selection Listener

When refreshing related data from a master grid selection, use a listener signature accepted by the component. A no-argument installed selection listener is invalid.

```java
import com.vaadin.flow.data.selection.SelectionEvent;

@Install(to = "ordersDataGrid", subject = "selectionListener")
private void ordersDataGridSelectionListener(SelectionEvent<DataGrid<Order>, Order> event) {
    Order order = ordersDataGrid.getSingleSelectedItem();
    if (order == null) {
        orderLinesDc.getMutableItems().clear();
        return;
    }

    orderLinesDl.setParameter("order", order);
    orderLinesDl.load();
}
```

If the project uses `@Subscribe("ordersDataGrid")` for selection changes, copy that existing compiled pattern instead of inventing a new listener form.

Do not append `.selected` to a container parameter. The container parameter itself represents the selected item for this loader binding.

```xml
where e.order = :container_ordersDc
```

Do not write:

```xml
where e.order = :container_ordersDc.selected
```

## Migrating from service call to entity create

To migrate a button from "collect a value, call a service" to "create an entity through its detail view", REMOVE the input dialog and call the detail dialog DIRECTLY. Do NOT chain an `InputDialog` before the detail dialog — a UI test that clicks the button expects a `StandardDetailView` immediately, and an `InputDialog` cast to `StandardDetailView` throws `ClassCastException`.

WRONG (broken hybrid):

```java
@Subscribe("actionButton")
public void onActionButtonClick(ClickEvent<JmixButton> event) {
    Order order = grid.getSingleSelectedItem();
    dialogs.createInputDialog(this)
            .withParameters(InputParameter.intParameter("quantity"))
            .withCloseListener(e -> {
                if (e.closedWith(DialogOutcome.OK)) {
                    Integer qty = e.getValue("quantity");
                    // then opens the detail dialog — too late, the test
                    // already saw the input dialog
                    openDetailView(order, qty);
                }
            })
            .open();
}
```

RIGHT (direct detail-dialog open):

```java
@Subscribe("actionButton")
public void onActionButtonClick(ClickEvent<JmixButton> event) {
    Order order = grid.getSingleSelectedItem();
    if (order == null) return;

    dialogWindows.detail(this, OrderLine.class)
            .newEntity()
            .withInitializer(line -> line.setOrder(order))
            .withAfterCloseListener(e -> {
                if (e.closedWith(StandardOutcome.SAVE)) {
                    ordersDl.load();   // reload the loader — NOT grid.getDataProvider().refreshAll()
                }
            })
            .open();
}
```

The detail view itself collects the scalar attributes; the entity is created on save; a side effect (e.g. an `EntityChangedEvent` listener) performs any derived update. The button just opens the detail dialog — nothing in between. "Open the X detail view from the button" means LITERALLY no `InputDialog`, no `MessageDialog`, and no service call before the dialog opens.

## Scalar Input Dialog

For a button that collects a simple scalar value and then calls a service, use the Jmix input dialog API instead of a raw Vaadin dialog.

```java
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;

@Autowired
private Dialogs dialogs;

@Subscribe("adjustButton")
public void onAdjustButtonClick(final ClickEvent<JmixButton> event) {
    dialogs.createInputDialog(this)
            .withHeader(messageBundle.getMessage("adjustDialog.header"))
            .withParameters(
                    InputParameter.intParameter("quantity")
                            .withLabel(messageBundle.getMessage("adjustDialog.quantity"))
                            .withDefaultValue(0)
            )
            .withActions(DialogActions.OK_CANCEL)
            .withCloseListener(closeEvent -> {
                if (closeEvent.closedWith(DialogOutcome.OK)) {
                    Integer quantity = closeEvent.getValue("quantity");
                    if (quantity != null) {
                        service.adjust(quantity);
                        affectedDl.load();
                    }
                }
            })
            .open();
}
```

Use message keys for dialog headers, labels, and notifications. Keep service calls in services; the view should only collect input, call the service, and reload loaders. (`messageBundle`, `service`, and `affectedDl` above are the view's existing injected dependencies — `MessageBundle`, your service bean, and the affected `CollectionLoader` — not re-shown here.)

## `IllegalArgumentException: argument type mismatch` on a button = WRONG HANDLER SIGNATURE

If clicking a button throws `IllegalArgumentException: argument type mismatch` (the error fires AT THE CLICK, before any dialog opens), the bug is the `@Install(subject="clickListener")` / `@Subscribe` handler PARAM TYPE — NOT the `InputParameter` or the dialog. A `clickListener` delivers a `ClickEvent<JmixButton>`; a handler typed `Component` or `ActionPerformedEvent` cannot receive it, so reflection throws.

```java
// WRONG — throws "argument type mismatch" at click:
@Install(to = "actionButton", subject = "clickListener")
private void onClick(final ActionPerformedEvent event) { ... }   // or (Component event)

// RIGHT:
@Subscribe("actionButton")
public void onClick(final ClickEvent<JmixButton> event) { ... }
```

Do NOT respond to this error by changing the `InputParameter` type (`intParameter`/`stringParameter`/`withType`/parse) — that is a different layer and leaves the click broken. Fix the handler param to `ClickEvent<JmixButton>` first. (`InputParameter.intParameter("q")` is the correct scalar form; do not switch to `stringParameter` + `Integer.parseInt`.)

## Whose data context does the dialog use

Whether the dialog's Save writes to the database at once or merges into the opening
view depends on how the dialog was opened. The default is the immediate write, and
`@Composition` on the attribute is what switches it off:

| opened with | parent context | what Save does |
| --- | --- | --- |
| `.withParentDataContext(ctx)` | yes | merges into the parent; reaches the DB when the parent is saved |
| a property-bound `<collection property="x"/>` container where `x` has `@Composition` — passed via `withContainer`, or through `list_create` / `list_edit` on a grid bound to that container | yes | same |
| a field whose value source is a `@Composition` reference (`withField`, one-to-one composition) | yes | same |
| a standalone container with a loader — this is the usual list view, including its standard `list_create` / `list_edit` | **no** | writes to the database immediately; the saved entity is then merged into the opener's `DataContext` for tracking only |
| a property-bound container or a field whose attribute is a plain association (**no** `@Composition`) | **no** | same as above |
| none of the above — plain `DialogWindows.detail(...)` | **no** | writes to the database immediately; Cancel upstream no longer undoes it |

Passing a container or a field is not by itself enough. The framework sets a parent data
context only for `@Composition` attributes: a property-bound container whose master
property is annotated, or a field bound to an annotated reference. Without the
annotation the dialog saves straight to the database, even though you passed the
container.

So opening someone else's entity from your view with a plain
`DialogWindows.detail(this, Customer.class).editEntity(customer)` is NOT part of the
opener's transaction: Save commits, and a later Cancel on the opening view leaves that
commit in place. The same is true of `list_create` in an ordinary list view.

- Independent on purpose: open without `withContainer` / `withField` /
  `withParentDataContext`, and refresh the opener by reloading its loader in
  `withAfterCloseListener` — not by attaching a container.
- Atomic with the parent: pass `.withParentDataContext(...)` explicitly. Do not rely on
  a container or a field to arrange it unless the attribute has `@Composition`.

When a parent data context is being set up, both views must be able to take part:

- The opened dialog view has no `DataContext` — `DevelopmentException: No DataContext in
  view '<id>'. Composition editing is impossible.` Add `<data>` with a writable context
  to the detail view.
- The opening view has a read-only context (`<data readOnly="true">`) —
  `IllegalArgumentException: Unsupported DataContext type ... Parent DataContext must
  implement DataContextInternal`. Drop `readOnly` on the opening view.
- The opening view has no `<data>` at all — no error, and no parent context either: the
  dialog quietly saves to the database.

## Forbidden

- Raw Vaadin `Dialog` for entity create/edit flows.
- Raw Vaadin `Dialog` for ordinary scalar input workflows.
- Direct service update when the domain requires creating an entity record.
- Chaining an `InputDialog`/`MessageDialog`/service call before opening the detail dialog when the button should open the detail view directly.
- Refreshing loaders after cancel/close when no save occurred.
- Refreshing grids with `getDataProvider().refreshAll()` instead of loader reload.
- Comparing entity-valued JPQL properties to UUID parameters.
- Using `.selected` inside a `:container_*` JPQL parameter.
- Calling `load()` on a loader after removing a JPQL parameter required by its query.
- A manual-`:param` loader (anything not `:container_*`/`:component_*`) placed under `<dataLoadCoordinator auto="true"/>` — it fires unbound at view open and throws. Omit it from the coordinator; load it from the selection handler after `setParameter`.
- No-argument installed selection listeners for `DataGrid` selection changes.
- A button `clickListener`/`@Subscribe` handler typed `Component` or `ActionPerformedEvent` — must be `ClickEvent<JmixButton>` (else `argument type mismatch` at click). Fix the handler signature, not the dialog/InputParameter.
- `stringParameter` + `Integer.parseInt` for a numeric input — use `InputParameter.intParameter(...)`.
