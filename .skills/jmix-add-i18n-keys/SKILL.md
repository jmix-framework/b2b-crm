---
name: jmix-add-i18n-keys
description: Add complete Jmix message keys for entities, enums, views, actions, and validation text, and configure official Jmix framework translations for non-English applications.
---

# Add I18n Keys

Use this skill whenever adding user-visible text, entities, enum values, views,
validation messages, or a non-English application locale.

## Steps

1. For a non-English application, add the official Jmix translation dependency for the target language before writing framework or add-on message overrides.
2. Find all locale files in the application message bundle.
3. Add the same application-owned keys to every locale file.
4. Use entity keys for entity captions and attributes.
5. Use enum keys for every enum constant.
6. Use view-local keys for titles, button text, labels, and dialog text.
7. Use `msg://` in XML descriptors.
8. Use `MessageBundle` in view controllers and `Messages` in services/beans.
9. Check every `msg://` reference against the bundle key exactly; key lookup is case-sensitive.
10. Use full `msg://<message-group>/<key>` references when the key belongs to another message group.

## Official framework translations

Application message bundles translate application-owned text only. For a
non-English application, add the official translation module for the target
locale to `build.gradle`:

```groovy
implementation 'io.jmix.translations:jmix-translations-<locale>'
```

Existing locales: `ar` (Arabic), `ckb` (Central Kurdish), `cs` (Czech), `da`
(Danish), `de` (German), `el` (Greek), `es` (Spanish), `fr` (French), `it`
(Italian), `nl` (Dutch), `pt-br` (Brazilian Portuguese), `ro` (Romanian), `ru`
(Russian), `tr` (Turkish), `zh-cn` (Simplified Chinese), and `zh-tw`
(Traditional Chinese).

Replace `<locale>` with one of these codes. Add only the dependencies for the
application's supported locales. Do not add a version: let the Jmix BOM resolve
the compatible version. Keep `jmix.core.available-locales` configured for the
locales exposed by the application.

Add this dependency before creating files such as
`src/main/resources/io/jmix/core/messages_<lang>.properties` or message bundles
inside add-on packages. Official translations normally contain most Jmix
framework and supported add-on messages, but some messages may be missing. Add
local overrides only for missing messages or intentional wording changes; do not
copy complete bundles that can become outdated after a Jmix upgrade.

Run the application in the target locale and inspect framework-owned views,
filter dialogs, and add-on views. Also inspect a Boolean value rendered by Jmix:
English `True` and `False` captions are a visible sign that the framework
translation is not active or does not contain those keys. Only add narrow
application overrides for messages that the official translation does not
provide or that the application intentionally changes.

## Key Patterns

```properties
com.company.app.entity/Customer=Customer
com.company.app.entity/Customer.name=Name

com.company.app.entity/OrderStatus=Order status
com.company.app.entity/OrderStatus.NEW=New

customerListView.title=Customers
customerDetailView.title=Customer
createOrderButton.text=Create order
```

## XML Usage

A Jmix message reference has two forms:

- Brief: `msg://<key>`.
- Full: `msg://<message-group>/<key>`.

The message group is usually the Java package-style bundle group before `/` in a properties key. Entity messages often use the entity package group, for example `com.company.app.entity/Customer.name`. View-local messages usually live in the view package group, for example `com.company.app.view.customer/customerListView.title`.

Brief references are resolved against the current XML descriptor message group. They are fine for keys stored next to that descriptor:

```xml
<view title="msg://customerListView.title">
    <button id="createOrderButton" text="msg://createOrderButton.text"/>
</view>
```

Use a full reference when the key is in another group, for example entity captions, menu keys, shared application keys, or text used from a descriptor whose package does not match the key group:

```xml
<item view="Customer.list" title="msg://com.company.app.view.customer/customerListView.title"/>
<h4 text="msg://com.company.app.entity/Customer.orders"/>
```

For Bean Validation messages, keep the same full reference inside braces:

```java
@NotNull(message = "{msg://com.company.app.entity/Customer.email.required}")
private String email;
```

## Java Usage In Views

```java
@ViewComponent
private MessageBundle messageBundle;

String text = messageBundle.getMessage("createOrderButton.text");
```

For localized entity/attribute captions (not bundle-key formatting), inject `io.jmix.core.MessageTools` and `Metadata`: `messageTools.getEntityCaption(metadata.getClass(Customer.class))` and `messageTools.getPropertyCaption(metadata.getClass(Customer.class), "name")`.

## Exact Reference Audit

Before finishing, perform both audit passes below.

### 1. Verify references

Search changed XML and Java for message references and verify the keys exist in
the correct bundle with identical casing.

```xml
<button id="createOrderButton" text="msg://createOrderButton.text"/>
```

```properties
createOrderButton.text=Create order
```

Do not rely on similar casing such as `CreateOrderButton.text` or `createorderButton.text`.

### 2. Verify translated values

Every locale bundle other than the one whose language the keys are authored in must be reviewed for leftover template
values — including in an `en,<other>` application, where the second bundle is generated as an English template and every
key in it resolves.

Read each such `messages_<locale>.properties` file complete, not only the lines
changed during the task. Project templates can contain English values under keys
that resolve correctly, so the reference audit does not detect them. Check seed keys
for the login view, main view, menu, and user entity as well as newly added keys.

For a locale that normally uses non-Latin text, use this search to find likely
English values left from the template:

```bash
rg -n '^[^#=]+=[\x00-\x7F]+$' \
  -g 'messages_<locale>.properties' src/main/resources
```

Review every match; this is a heuristic, not proof of an error. Product names,
URLs, abbreviations, numbers, and intentionally untranslated technical terms can
be valid. Values such as `MainView.title=App`, `User=User`, or
`loginForm.username=Username` usually require translation in a non-English-only
application.

When the task adds keys to a bundle that is still an untouched template, pick one of
two outcomes and say which — translating the whole bundle, or translating only the
new keys and recording the rest as a known gap. Both are defensible. Translating the
new keys and silently leaving the seed keys is not: it produces a half-translated UI
that reads as an oversight.

## Forbidden

- Hardcoded user-visible text in XML or Java controllers.
- Adding a key to only one locale file.
- `msg://` keys that differ from properties keys only by case.
- Brief `msg://key` references to keys stored in another message group.
- Missing enum constant messages.
- `${0}` placeholders in `formatMessage`; use Java formatter placeholders such as `%s`.
- Copies of framework or add-on message bundles created before checking for an
  official `jmix-translations-<lang>` artifact.
- English template values left without explicit review in any locale bundle other
  than the one the keys are authored in — an `en,<other>` application included.
