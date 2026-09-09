---
name: jmix-create-test
description: Create or update Jmix unit, integration, UI integration, or end-to-end tests for services, entity listeners, security behavior, views, fragments, and persistence workflows.
---

# Create Test

Use this skill when adding or changing tests for Jmix application behavior.

## Steps

1. Choose the smallest test type that proves the behavior.
2. Use a plain JUnit test for pure Java logic without Spring/Jmix services.
3. Use `@SpringBootTest` for services, DataManager persistence, entity listeners, security, and transactions.
4. Use `@UiTest` with `FlowuiTestAssistConfiguration` for Flow UI controller/component behavior without a browser.
5. Use end-to-end browser tests only for real browser behavior, routing, login, theme, or Vaadin client-side interactions.
6. Create test data through `DataManager.create()` and `DataManager.save()`.
7. Set authentication with the project's `AuthenticatedAsAdmin` extension or `SystemAuthenticator`.
8. Clean up created persistent data in `@AfterEach`.
9. Mock external systems at the boundary; prefer `@MockitoBean` on Spring Boot 3.4+ projects and follow the project's existing compiled pattern otherwise.
10. Run the smallest relevant Gradle test command.

Before typing any Jmix/Vaadin symbol you didn't copy from this project's `src` — a class, enum constant, action id, component, or event inner-class — confirm it. A guessed symbol survives typing and then breaks `compileJava`. Use the Context7 MCP (`/jmix-framework/jmix-context7`) when available, otherwise the official docs plus a working example already in this repo — see `jmix-verify-api-symbol`.

## Unit Test Pattern

```java
class PriceCalculatorTest {
    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    void appliesDiscount() {
        assertThat(calculator.applyDiscount(100, 10)).isEqualTo(90);
    }
}
```

## Integration Test Pattern

```java
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class CustomerServiceTest {
    @Autowired
    private DataManager dataManager;

    @Autowired
    private CustomerService customerService;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void findsCustomerByEmail() {
        Customer customer = dataManager.create(Customer.class);
        customer.setEmail("customer@test.com");
        cleanup.add(dataManager.save(customer));

        assertThat(customerService.findByEmail("customer@test.com")).isPresent();
    }

    @AfterEach
    void tearDown() {
        cleanup.forEach((entity) -> dataManager.load(Id.of(entity)).optional().ifPresent(dataManager::remove));
    }
}
```

When the code under test (a service method, an entity event listener) may call `dataManager.save()` on the registered entity a second time, the reference in the cleanup list goes stale — its version lags the database — and `remove` in `@AfterEach` throws an optimistic-lock exception. So use the id to reload-then-remove.

## Security Test Pattern

```java
Optional<Customer> result = systemAuthenticator.withUser(
        username,
        () -> customerService.findByEmail("customer@test.com")
);
```

Use this when the expected result depends on Jmix security policies.

## UI Integration Test Pattern

```java
@UiTest
@SpringBootTest(classes = {AppApplication.class, FlowuiTestAssistConfiguration.class})
class CustomerUiTest {
    @Autowired
    private ViewNavigators viewNavigators;

    @Test
    void opensCustomerList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), CustomerListView.class).navigate();
        CustomerListView view = UiTestUtils.getCurrentView();
        DataGrid<Customer> grid = UiTestUtils.getComponent(view, "customersDataGrid");
        assertThat(grid).isNotNull();
    }
}
```

Use the project's helper for component lookup if it exists. Otherwise keep a local typed helper small and explicit. A `@UiTest` that navigates to a view will fail to find it if the view's id or the component id is wrong — the test, not just `compileJava`, is what catches that.

`UiTestUtils.getCurrentView()` works as the navigation origin even before the
test navigates anywhere: `@UiTest` opens the initial view before each test, so a
current view always exists.

**Authenticate as a database user when behavior depends on the current user.** By
default, `@UiTest` uses the system user. The system user is created at application
startup and has no row in the application's user table. Code that resolves the
current user through `DataManager` or the user repository therefore finds no
entity and can silently take a "not the current user" branch. Wrap navigation,
interaction, and assertions in `runWithUser` so the whole UI operation uses a
real test user:

```java
systemAuthenticator.runWithUser("admin", () -> {
    viewNavigators.detailView(UiTestUtils.getCurrentView(), Order.class)
            .editEntity(order)
            .navigate();
    OrderDetailView view = UiTestUtils.getCurrentView();
    JmixButton approveButton = UiTestUtils.getComponent(view, "approveButton");
    assertThat(approveButton.isVisible()).isTrue();
});
```

Use `withUser` instead when the authenticated block returns a value. This is
separate from testing security policies: authenticate whenever the result
depends on which application user is looking at the data.

`runWithUser` inside the test body cannot cover code that runs before the test:
`@UiTest` opens the initial/main view first, still as the system user. When that
view's code resolves the current user — or every test in the class needs the
same real user — implement `UiTestAuthenticator` and pass it in
`@UiTest(authenticator = ...)` so the whole class, including the initial view,
runs as that user.

**Select an inactive `tabSheet` tab before clicking its components.** Component
lookup and `@ViewComponent` injection can find a component on a non-selected tab,
but `JmixTabSheet` explicitly disables the content of every non-selected tab
(and attaches it to the component tree only when its tab is first selected).
`Button.click()` checks `isEnabled()` first, so on that content it is a silent
no-op and does not call its subscribed handler. This is specific to inactive tab
content — a merely detached component, such as one built in a controller and not
yet added anywhere, clicks normally. To test the component interaction, select
the tab first:

```java
JmixTabSheet tabSheet = UiTestUtils.getComponent(view, "orderTabSheet");
JmixButton approveButton = UiTestUtils.getComponent(view, "approveButton");

tabSheet.setSelectedIndex(1); // enable the tab content that contains approveButton
approveButton.click();
```

If the test targets only the server-side handler, fire the event directly and
state why you bypass the disabled component:

```java
// The button is on an inactive tab; invoke the server-side handler directly.
ComponentUtil.fireEvent(approveButton, new ClickEvent<>(approveButton));
```

Direct event firing does not prove that the tab selection and browser click path
work. A silent `click()` on the disabled content also does not prove that the
button is broken in a browser. Use a browser test when that client-side path is
the behavior under test.

**List a nested `@TestConfiguration` in `classes` explicitly.** Do not rely on the
usual "a static `@TestConfiguration` inside the test class is picked up
automatically" behaviour once `@SpringBootTest(classes = {...})` names an explicit
set, as `@UiTest` requires — in practice the nested class's beans do not appear.
Name it yourself:

```java
@UiTest
@SpringBootTest(classes = {
        AppApplication.class,
        FlowuiTestAssistConfiguration.class,
        CustomerUiTest.MockApiConfig.class})   // ← nested config, listed EXPLICITLY
class CustomerUiTest {

    @TestConfiguration
    static class MockApiConfig { /* @Bean overrides */ }
}
```

Omitting it does not fail loudly: the beans simply never get defined, and the test
runs against the real collaborator (a live HTTP call, or a `NoSuchBeanDefinition`
far from the cause).

## Reaching what is NOT a component on the form

`UiTestUtils.getComponent(...)` only finds components in the view. Dialogs and
notifications are NOT children of the `UI` — walking `UI.getCurrent().getChildren()`
compiles, runs, and finds nothing (the UI's children are just the main view). The
resulting failure reads as "the dialog never opened", accusing correct view code.

```java
DialogInfo dialog = UiTestUtils.getLastOpenedDialog();
assertThat(dialog.getText()).contains("...");
dialog.getButtons().stream()
        .filter(b -> "YES".equals(b.getText()))
        .findFirst().orElseThrow()
        .click();
```

The same class also provides `getOpenedDialogs()`, `getLastOpenedNotification()`,
`getOpenedNotifications()` and `validateView(detailView)` — assert dialogs,
notifications and validation errors through these, never by traversing the tree.

Data containers are reached through `ViewControllerUtils`, because `View.getViewData()`
is protected and the compiler does not point anywhere:

```java
CollectionContainer<Category> dc =
        ViewControllerUtils.getViewData(view).getContainer("categoriesDc");
assertThat(dc.getItems()).allMatch(Category::isApplicable);
```

## Testing code that runs outside a user session

A scheduler / `@Async` / application event listener path has no authenticated user, and a test that
sets authentication up **hides** exactly the defect worth catching. Call the entry
point bare, and authenticate only the data setup:

```java
@Test
void generateDailyReportsRunsOnTheSchedulerThread() {
    systemAuthenticator.runWithSystem(() -> givenOrder());   // setup only
    // no authentication around the call — reproduces the scheduler thread
    reportService.generateDailyReports();
}
```

See `jmix-run-background-code` for the `@Authenticated` / `SystemAuthenticator`
rules on the production side.

## End-To-End Tests

Use Masquerade/Selenide or the project's browser-test stack when browser verification is required. Enable test ids only in a test profile and prefer stable component ids over text selectors.

## Cleanup Audit

Before finishing, check:

- Every created persistent record is removed in `@AfterEach`, using the same authentication level needed for deletion.
- Cleanup reloads by id then removes instead of removing the instance that can be stale.
- Test data has unique values to avoid collisions.
- Assertions verify persisted or visible behavior, not just absence of exceptions.
- UI tests that depend on who is viewing the data authenticate as a real database user around navigation, interaction, and assertions.
- UI tests select the containing `tabSheet` tab before calling `click()` on a component in that tab, unless they intentionally fire the server-side event directly and explain why.
- The test command can run one class or method without running the full suite.
- No `@Transactional` on the test class or methods — it rolls back the writes that `@AfterEach` cleanup and the twice-back-to-back run depend on.
- Run the single class twice back-to-back — a second green run proves no leaked rows or cross-test data dependence that one pass hides.
- `@AfterEach` deletions run in FK-safe order: child rows (holding a `@ManyToOne`/`@JoinColumn` to another cleaned-up entity) before the parents they reference. Re-check this whenever a new FK is added to an entity already covered by an existing cleanup block — a previously-safe order can silently become unsafe and throw a constraint violation in teardown, which JUnit reports against the test that triggered it rather than the cleanup, so it first reads as a test bug rather than an ordering one.
- An in-memory datasource with a FIXED name (`jdbc:hsqldb:mem:<fixed>`) is shared for the whole JVM/test run across every `@SpringBootTest` context that resolves to it — it is NOT reset per class or per method (HSQLDB's `mem:` catalog is registered by name for the JVM's lifetime). So `@AfterEach` cleanup is mandatory even for a test that only reads, or a later test in the same run sees its leftover rows; and a test that calls the same method twice to prove idempotency must not assume a fresh precondition between the two calls — set up explicitly the precondition the code under test actually checks.

## Forbidden

- `@Transactional` on test classes/methods — it rolls back writes, so `@AfterEach` cleanup of committed entities and running the test twice back-to-back both stop working.
- `@MockBean` (deprecated) — use `@MockitoBean`.
- `new Entity()` or constructor-created Jmix entities in persistence tests.
- Tests that depend on data left by previous tests.
- Cleanup only at the end of the test method.
- UI tests that assert only that navigation did not throw.
- Calling `click()` on a component in a non-selected `tabSheet` tab and treating the silent no-op as evidence about browser behavior.
- Leaving `@UiTest` as the system user when the behavior depends on resolving the current user from the database.
- Browser tests for behavior that `@UiTest` or service tests can prove.
- Hardcoded sleeps when framework waits or component assertions are available.
- `@WithUserDetails` for Jmix security tests when `SystemAuthenticator` or the project auth extension is available.
- A nested `@TestConfiguration` left out of `@SpringBootTest(classes = {...})` — its beans are silently never defined.
- `spring.main.allow-bean-definition-overriding=true` to replace a bean for one test — use a distinct `@Primary` bean instead.
- Authenticating before calling a scheduler/`@Async` entry point and then claiming that path is covered.
