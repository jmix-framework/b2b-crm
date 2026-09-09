---
name: jmix-create-detail-view
description: Create a Jmix Flow UI detail view with XML descriptor, save-close action, messages, and policies.
---

# Create Detail View

Use this skill when creating a create/edit view for one entity.

## Two render-time killers that compile clean

Both produce valid Java/XML — `compileJava` is green — and then throw
when the view is opened. `compileJava` is BLIND to `*-view.xml`; only a
Jmix-aware inspection catches them statically, and the mechanical checks
do NOT cover these two. With no inspection, get them right BY
CONSTRUCTION from the WRONG/RIGHT examples below:

1. **An enum attribute is NEVER `entityComboBox`.** `entityComboBox`
   is for ENTITY references; binding it to an enum (with or without a
   made-up `enumClass` attribute) throws `IllegalStateException: Range
   is enumeration` at render. There is no `enumClass` attribute on
   `entityComboBox`. For a Jmix enum property use a plain `<comboBox>`
   or `<select>` — Jmix auto-populates it from the enum:

   ```xml
   <!-- WRONG: <entityComboBox property="category" enumClass="...SomeEnum"/> -->
   <comboBox id="categoryField" property="category"/>   <!-- enum: just bind the property -->
   ```

2. **`itemsQuery` MUST wrap its JPQL in a nested `<query>` element**, and that
   query MUST reference `:searchString` (the combo passes it for type-ahead).
   Raw CDATA directly under `<itemsQuery>` throws
   `GuiDevelopmentException: Nested 'query' element is missing`; a query that
   ignores `:searchString` throws `DevelopmentException: Parameter 'searchString'
   is not used in the query` at dropdown fetch. See "Reference Fields" below for
   the correct shape.

## Read-only open mode vs read-only descriptor

These are two DIFFERENT things. Do not conflate them.

**Read-only OPEN MODE** is set by the LIST view's open action:
`list_read` instead of `list_edit`. `list_read` is an open MODE
(read-only at runtime), not a `readOnly` descriptor. Users opening an
existing row see a non-editable form; new entities still open in
writable mode through `list_create`.

**Read-only DESCRIPTOR** would mean every form field is hard-coded
`readOnly="true"` and the detail view has no save action — only
`detail_close`. This makes the view ALWAYS read-only, even for new
entities. It CANNOT create entities and CANNOT save edits.

"The list opens records in read mode" or "the detail view is opened
with the `read` action" — both phrasings call for read-only OPEN MODE
on the LIST side. The detail XML must still be a normal writable
descriptor with `detail_saveClose` and editable fields. Jmix flips
fields to read-only at runtime based on the open mode.

A detail view without a save action is broken for create flows.
Always declare both:

```xml
<actions>
    <action id="saveCloseAction" type="detail_saveClose"/>
    <action id="closeAction" type="detail_close"/>
</actions>
```

and use editable form components (`textField`, `comboBox`,
`entityComboBox`, etc.) without hard-coded `readOnly="true"` unless
a specific attribute is permanently read-only in domain terms (e.g.
audit timestamps).

## Steps

1. Create Java controller under `view/<entityname>/`.
2. Extend `StandardDetailView<Entity>`.
3. Add `@Route(value = ".../:id", layout = MainView.class)`.
4. Add `@ViewController(id = "Entity.detail")`.
5. Add `@ViewDescriptor(path = "entity-detail-view.xml")`.
6. Add `@EditedEntityContainer("<entity>Dc")`.
7. Create XML descriptor with instance container, loader, `dataLoadCoordinator`, typed form fields, `detail_saveClose`, and `detail_close`.
8. Configure reference fields with a verified data source: lookup action, `itemsContainer`, or `itemsQuery`.
9. Use `InitEntityEvent` for UI-only defaults ONLY. A required persistent default must be set at the ENTITY layer (field initializer / `@PostConstruct` / `EntitySavingEvent`) — NOT in an `InitEntityEvent` alone, NOT in a service, NOT in `EntityChangedEvent` (it fires after persist and cannot satisfy `@NotNull`). Tests save via `DataManager` and bypass the view — see `jmix-create-entity` (required-field defaults).
10. Add message keys for the title and field labels.
11. Grant access for roles that can open the detail view with `@ViewPolicy(viewIds = "Entity.detail")` — declared on a **method of a `@ResourceRole` interface** (the annotation is `@Target(METHOD)`), not on the view controller. The annotation has no `value()` member: `@ViewPolicy("...")` does not compile; use `viewIds = "..."` (or `viewClasses = ...`). See `jmix-create-resource-role`.
12. Before finishing, compare every form field component against the Java property type.

## Controller Template

```java
@Route(value = "customers/:id", layout = MainView.class)
@ViewController(id = "Customer.detail")
@ViewDescriptor(path = "customer-detail-view.xml")
@EditedEntityContainer("customerDc")
public class CustomerDetailView extends StandardDetailView<Customer> {
}
```

## XML Skeleton

```xml
<view xmlns="http://jmix.io/schema/flowui/view"
      title="msg://customerDetailView.title"
      focusComponent="form">
    <data>
        <instance id="customerDc" class="com.company.app.entity.Customer">
            <fetchPlan extends="_base"/>
            <loader id="customerDl"/>
        </instance>
    </data>
    <facets>
        <dataLoadCoordinator auto="true"/>
    </facets>
    <actions>
        <action id="saveCloseAction" type="detail_saveClose"/>
        <action id="closeAction" type="detail_close"/>
    </actions>
    <layout>
        <formLayout id="form" dataContainer="customerDc">
            <textField id="nameField" property="name"/>
        </formLayout>
        <hbox id="detailActions">
            <button id="saveAndCloseButton" action="saveCloseAction"/>
            <button id="closeButton" action="closeAction"/>
        </hbox>
    </layout>
</view>
```

## Field Component Mapping

Choose form components by property type:

| Property type | Component |
| --- | --- |
| `String` short text | `textField` |
| `String` long text | `textArea` |
| `Integer` | `integerField` |
| `Long` | `integerField` or `numberField` according to project usage |
| `BigDecimal` | `bigDecimalField` |
| `Boolean` | `checkbox` |
| `LocalDate` | `datePicker` |
| `LocalDateTime` | `dateTimePicker` |
| Jmix enum | `select` or `comboBox` |
| Entity reference | `entityComboBox` or `entityPicker` |

For a `Boolean` property annotated with `@NotNull`, `false` is still a valid
value: the constraint rejects only `null`. A bound checkbox inherits the
required state from the entity metadata, but Vaadin treats an unchecked checkbox
as empty. The form then rejects `false` and the save action leaves the view open.
Declare `required="false"` explicitly so the checkbox can represent both Boolean
values without weakening the entity constraint:

```xml
<checkbox id="subscribedField" property="subscribed" required="false"/>
```

Do not expose technical fields (`id`, `version`) in user-facing forms. Hide parent/default fields only when they are initialized elsewhere.

## Final XML Type Audit

After creating or editing the descriptor, inspect each field:

- `Integer` is not a `textField`; use `integerField`.
- `BigDecimal` is not a `textField`; use `bigDecimalField`.
- Date/time properties use date/time picker components.
- Boolean properties use checkbox or the project's boolean component pattern. A
  checkbox bound to an `@NotNull Boolean` that may be `false` declares
  `required="false"` explicitly.
- Entity references use reference components, not text fields.

If an existing project uses a different compiled pattern for a type, follow the existing pattern and keep it consistent.

## Reference Fields

For `@ManyToOne` and other entity references, pick the pattern by candidate-set characteristics:

- **`entityComboBox` with `itemsQuery` — PREFERRED.** Lazy-loading: the JPQL runs against the database on every type-ahead, fetching only matching candidates. Use this for any non-trivial candidate set.
- **`entityComboBox` with `itemsContainer`** — for small, fixed sets that fit comfortably in memory and rarely change. Preloaded once at view open.
- **`entityPicker`** with lookup and clear actions — when users need a full lookup screen. Use the literal standard action ids `entity_lookup` and `entity_clear` (do not invent ids).

### `itemsQuery` — the correct shape

The combo passes a `searchString` parameter for type-ahead, so the
JPQL MUST `like :searchString`; ignoring it throws `DevelopmentException:
Parameter 'searchString' is not used in the query` at dropdown fetch.
The JPQL must use the JPA/Jmix entity name, not the database table
name.

```xml
<entityComboBox id="refField" property="ref">
    <itemsQuery class="com.company.app.entity.Ref">
        <query>
            <![CDATA[
            select e from Ref e
            where e.name like :searchString
            order by e.name
            ]]>
        </query>
    </itemsQuery>
</entityComboBox>
```

`itemsQuery` does NOT auto-bind `:container_*` / `:component_*`
parameters — when the reference list depends on another component or
container, use `itemsContainer` with a regular loader instead (see
below).

### `itemsContainer` — when the set is small or container-dependent

Declare a `<collection>` for the candidate entities and point the combo
at it:

```xml
<data>
    <instance id="<entity>Dc" class="com.company.app.entity.<Entity>">
        <fetchPlan extends="_base">
            <property name="ref" fetchPlan="_instance_name"/>
        </fetchPlan>
        <loader id="<entity>Dl"/>
    </instance>
    <collection id="refsDc" class="com.company.app.entity.Ref">
        <fetchPlan extends="_instance_name"/>
        <loader id="refsDl">
            <query><![CDATA[select e from Ref e order by e.name]]></query>
        </loader>
    </collection>
</data>
...
<entityComboBox id="refField" property="ref" itemsContainer="refsDc"/>
```

`<dataLoadCoordinator auto="true"/>` loads `refsDc` at open. Use this
when the candidate set is small enough to fit in memory, or when the
loader takes a `:container_*` / `:component_*` parameter that
`itemsQuery` cannot bind.

Before finishing, verify that saved reference entities can appear in the component data provider. If a field is required, do not leave a reference component without a working item source or lookup action.

## To-many data grid

To show a to-many composition or association (`Order.products`, `Customer.tags`) on a detail
view, nest a property-bound `<collection>` INSIDE the `<instance>` container and
bind a `dataGrid` to it. The container infers its metaclass from the property, so
a `class` attribute here is a descriptor defect:

```xml
<instance id="orderDc" class="com.company.app.entity.Order">
    <fetchPlan extends="_base">
        <property name="products" fetchPlan="_instance_name"/>
    </fetchPlan>
    <loader id="orderDl"/>
    <!-- property-bound: NO class, NO loader, NO query -->
    <collection id="productsDc" property="products"/>
</instance>
```

```xml
<dataGrid id="productsDataGrid" dataContainer="productsDc" width="100%">
    <columns>
        <column property="name"/>
    </columns>
</dataGrid>
```

The trap is that a TOP-LEVEL `<collection>` (a list view's own loader) REQUIRES
`class`. A nested property-bound collection takes `property` and nothing else — no `class`,
no loader, no query. The parent's fetch plan must include the property, or the
grid is empty (see `jmix-configure-fetch-plan`).

For a read-only grid, declare no `list_create` / `list_edit` / `list_remove`
actions on it.

## Styling

Styling a field, a card, or a component you build in the controller — CSS classes,
component theme variants, `getStyle().set(...)`, `--aura-*` / `--lumo-*` tokens —
is covered by `jmix-style-ui`. Read it before typing a CSS custom property: a
token the active theme does not define fails silently and passes every gate.

## Asking for confirmation before saving

`ValidationEvent` can only REJECT a save; it cannot ask a question and continue.
The only hook that suspends a save and resumes it afterwards is
`StandardDetailView.BeforeSaveEvent` with `preventSave()` / `resume()`:

```java
@Autowired
private Dialogs dialogs;

@Subscribe
public void onBeforeSave(final BeforeSaveEvent event) {
    if (!reissuesRelatedCodes()) {
        return;
    }
    event.preventSave();
    dialogs.createOptionDialog()
            .withHeader(messageBundle.getMessage("confirmDialog.header"))
            .withText(messageBundle.getMessage("confirmDialog.text"))
            .withActions(
                    new DialogAction(DialogAction.Type.YES)
                            .withHandler(e -> event.resume()),
                    new DialogAction(DialogAction.Type.NO))
            .open();
}
```

Note `Dialogs.createOptionDialog()` takes NO view argument, unlike
`createInputDialog(View)`. Without `preventSave()` the save runs on regardless of what
a dialog is showing, so a home-made confirmation dialog does not hold anything back.

## Cross-field validation

For cross-field/manual validation, add a `@Subscribe` handler on `ValidationEvent` and report failures via `event.getErrors().add("...")`; for programmatic checks (e.g. before a custom save) use the `ViewValidation` bean (`validateUiComponents`, `showValidationErrors`).

## Forbidden

- Using list-view route or id patterns for detail views.
- Missing `detail_saveClose`.
- Using `textField` for numeric, date/time, boolean, or reference properties.
- Reference fields without a working lookup action, `itemsContainer`, or verified `itemsQuery`.
- Using `@Table` names in `itemsQuery`.
- `itemsQuery` with unresolved `container_` or `component_` parameters.
- Hardcoded labels or titles.
- Hiding required fields without setting defaults elsewhere.
- A checkbox bound to an `@NotNull Boolean` without `required="false"` when
  unchecked `false` is a valid value.
- Missing view policy for dialog-opened detail views.
- A `class` attribute on a nested property-bound `<collection property="..."/>`.
- `<markdown>` with an empty or absent `content` attribute (throws at view load), or a guessed `io.jmix.flowui...Markdown` import.
- CSS custom properties from a theme the app does not run (e.g. `--lumo-*` in an Aura app) — see `jmix-style-ui`.
