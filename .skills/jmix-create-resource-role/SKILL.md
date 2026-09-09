---
name: jmix-create-resource-role
description: Create or update Jmix resource roles with entity, attribute, view, and menu policies, including the CREATE-implies-MODIFY rule.
---

# Create Resource Role

Use this skill when adding or changing Jmix security access.

The model is ADDITIVE / no-deny: if any assigned role grants access the user has
it, and there is no deny-policy. A role interface may `extend` several role
interfaces of the SAME kind to compose their policies (a role cannot mix
`@ResourceRole` and `@RowLevelRole`).

## TOP RULE — CREATE implies MODIFY

If a role grants `EntityPolicyAction.CREATE` on an entity, that entity's
`@EntityAttributePolicy` for the editable fields MUST be
`EntityAttributePolicyAction.MODIFY` — NEVER `VIEW`. This holds even when the
entity is immutable after creation (`READ` + `CREATE` only, no `UPDATE`/`DELETE`):
`VIEW` is read-only, so a user with `CREATE + VIEW` can persist a row but cannot
fill its fields. At runtime an attribute with only a `VIEW` policy is modify-denied
(`EntityAttributeContext.canModify()` returns false), so a `CREATE + VIEW` user gets a
create form whose fields are all read-only — unusable.

```java
// CORRECT for an immutable-after-create entity:
@EntityAttributePolicy(entityClass = <Entity>.class,
        attributes = "*", action = EntityAttributePolicyAction.MODIFY)   // MODIFY, not VIEW
@EntityPolicy(entityClass = <Entity>.class,
        actions = { EntityPolicyAction.READ, EntityPolicyAction.CREATE })
void entityPolicy();
```

Apply this to EVERY role consistently. A frequent mistake is getting the manager
role right (`MODIFY`) but leaving a read/employee role on `VIEW` while it still has
`CREATE` — both must be `MODIFY` if both have `CREATE`. If some attributes must be
read-only even at creation (e.g. auto-generated audit fields), exclude them from the
`attributes` list or use a second `VIEW` policy for that subset — NOT a blanket
`VIEW` on `*`.

## Where attribute policies are enforced

An attribute policy is enforced by the Flow UI value bindings and grid columns,
and by `EntitySerialization` — NOT by `DataManager`. The data layer checks
entity-level and row-level rules only. So a service, a scheduled job, or a custom
controller running under the role's session persists an attribute the role was
never granted, with no error. Guard the non-granted attributes in that code
yourself; the policy will not do it for you.

## Requirement wording → policy actions

Map the EXACT wording of the requirement to entity-policy actions. Re-read the
requirement for the entity BEFORE writing the policy block.

| Requirement wording (about an entity)           | EntityPolicyAction         |
|-------------------------------------------------|----------------------------|
| "view only", "read only"                        | `READ`                     |
| "view and create", "create only"                | `READ`, `CREATE`           |
| "view, edit"                                    | `READ`, `UPDATE`           |
| "view, create, delete" (no update)              | `READ`, `CREATE`, `DELETE` |
| "full CRUD", "manage", "all operations"         | `ALL`                      |
| "cannot be updated", "immutable"                | do NOT include `UPDATE`    |
| "cannot be deleted"                             | do NOT include `DELETE`    |
| "cannot be updated or deleted"                  | `READ`, `CREATE` only — even for managers |

For an entity described as "cannot be updated or deleted", the role must NOT
grant `UPDATE` or `DELETE` — not even for the manager role.
`EntityPolicyAction.ALL` includes both and contradicts the "cannot be updated or deleted" requirement. Note
`list_read` is an open MODE (read-only access), not a `readOnly` descriptor.

## Composition children

For every entity that appears as a `composition` property on another entity, the
child's detail view is opened as a DIALOG from the parent's detail view. That
dialog is a real Jmix view subject to `@ViewPolicy`, even though users never reach
it from a menu — so it has no `@MenuPolicy` entry. Always include the child detail
view id in `@ViewPolicy` for every role that can edit the parent.

```java
@ViewPolicy(viewIds = {
    "<Parent>.list",
    "<Parent>.detail",
    "<Child>.detail",          // composition child, opened from <Parent>.detail
})
@MenuPolicy(menuIds = {"<Parent>.list"})   // child detail is NOT in menu
void parentScreens();
```

A `@ViewPolicy` that lists parent list+detail but omits the child detail will pass
compilation and fail at runtime when the user clicks "+" inside the parent's
composition table.

## Row-Level roles

Row-level roles are a separate first-class concept from resource roles: a resource
role grants *what* you can do, a row-level role restricts *which rows* you see. They
live in their own interface annotated with `@RowLevelRole` and never mix with
`@ResourceRole`.

- `@JpqlRowLevelPolicy(entityClass = ..., where = "...")` filters at the database
  level. Use `{E}` as the entity alias and `:current_user_*` params (e.g.
  `:current_user_username`).
- `@PredicateRowLevelPolicy(entityClass = ..., actions = {...})` filters in-memory;
  the method returns a `RowLevelPredicate` / `RowLevelBiPredicate`. Use for logic
  that JPQL cannot express and for non-read operations.

Gotcha: a JPQL policy only affects the root entity of a loaded graph. If the same
entity is also loaded as a *collection* inside another entity's graph, define BOTH
a `@JpqlRowLevelPolicy` and a `@PredicateRowLevelPolicy` for it to keep access
consistent.

```java
@RowLevelRole(name = "Own Orders Only", code = "app_OwnOrdersOnly")
public interface OwnOrdersOnlyRole {
    @JpqlRowLevelPolicy(entityClass = Order.class,
            where = "{E}.createdBy = :current_user_username")
    void orderPolicy();
}
```

## Mechanical self-check before finishing

A clean compile is NOT "done" — most role defects survive `compileJava` and surface
only at render or in tests. Confirm these with concrete checks, not by re-reading
your own code:

1. **CREATE ⇒ MODIFY.** For every role file, find each entity granted
   `EntityPolicyAction.CREATE` and confirm its `@EntityAttributePolicy` action on
   the editable attributes is `MODIFY`, not `VIEW`. A `CREATE` paired with a `VIEW`
   attribute policy leaves every create-form field read-only at runtime — a common, easy-to-miss mistake.
2. **`@MenuPolicy` uses LEAF ids, not the group id.** Read `menu.xml`. The ids must
   be the exact `<item>` ids/views the user opens; granting the enclosing
   `<menu id="...">` GROUP id does NOT grant its items.
3. **Every reachable view has a `@ViewPolicy` entry.** Include composition-dialog
   detail views opened from a parent grid even though they have no menu item.

To assert a permission in Java at runtime, inject `AccessManager` and call
`applyRegisteredConstraints(...)` on a context (e.g. `EntityOperationContext`), then
check `isPermitted()`.

## Steps

1. List every entity the role must access.
2. For each entity, decide CRUD actions explicitly.
3. Add attribute policies for fields the role must view or edit.
4. List every view the role can open, including dialog-only detail views.
5. Add menu policies only for real menu item ids from `menu.xml`, not parent grouping ids.
6. Do not grant broader actions than the workflow requires.
7. Re-check create-only workflows: create forms need `MODIFY` attribute access even when update/delete are forbidden.
8. If saving an allowed record triggers service side effects on another entity, ensure the operation has the needed permissions or is implemented through an appropriate trusted service path.

## Read/Create Without Update/Delete

```java
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(name = "Employee", code = EmployeeRole.CODE)
public interface EmployeeRole {
    String CODE = "employee";

    @EntityAttributePolicy(entityClass = OrderRequest.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = OrderRequest.class,
            actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE})
    void orderRequestEntity();

    @ViewPolicy(viewIds = {
            "OrderRequest.list",
            "OrderRequest.detail"
    })
    @MenuPolicy(menuIds = "OrderRequest.list")
    void orderRequestScreens();
}
```

## Role Matrix

| Surface | Required? | Policy |
| --- | --- | --- |
| Entity CRUD | yes | `@EntityPolicy` |
| Entity attributes | yes | `@EntityAttributePolicy` |
| List view | if user opens it | `@ViewPolicy` |
| Detail/dialog view | if user opens it | `@ViewPolicy` |
| Menu item | if user opens it from menu | `@MenuPolicy` |

## Menu Policy Audit

Menu groups are navigation containers, not the user-facing item policies most roles
need. Read `menu.xml` and grant the item ids that open views.

```xml
<menu id="sales" title="msg://menu.sales">
    <item view="Customer.list"/>
    <item id="orders" view="Order.list"/>
</menu>
```

```java
@MenuPolicy(menuIds = {"Customer.list", "orders"})
```

Do not grant only `sales` unless the role is intentionally controlling the parent
group itself and the project's security checks use that group id.

## Forbidden

- `EntityPolicyAction.ALL` when update/delete are not required.
- `UPDATE` or `DELETE` entity actions for immutable or create-only records.
- Entity create permission without `MODIFY` attribute permission for editable fields.
- View policies only for list views while create/edit dialogs use detail views.
- Menu policy for a parent group when the user needs access to concrete menu items.
- Menu policy for views that are not menu entries.
