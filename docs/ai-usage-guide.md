# AI Usage Guide for `mol`

This file is written for AI coding agents that inspect the `mol` source before modifying a Paper plugin. It describes the intended design, invariants, and safe extension points.

## Mental Model

`mol` is a narrow Paper plugin utility library for projects that share the same runtime, configuration, localization, and command-registration patterns.

The intended consumer pattern is:

1. a `JavaPlugin` owns one active runtime,
2. the runtime owns config-backed runtime-scoped collaborators,
3. localized messages are loaded from `languages/*.yml`,
4. listeners and scheduled tasks must die on reload,
5. commands must always use the current runtime.

Do not reinterpret `mol` as a dependency injection container, platform abstraction, or command DSL. Prefer explicit plugin code with a small shared runtime base unless a real repeated use case proves another abstraction is needed.

## Public API Map

### `io.github.tetratheta.mol.plugin.BasePlugin<R extends PluginRuntime>`

Purpose:

- finalizes `onEnable()` and `onDisable()`
- creates and terminates one typed runtime
- exposes `getRuntime()`
- reloads the runtime with `reloadRuntime()`
- registers Paper command nodes with `registerCommand(...)`

Subclass hooks:

- `createRuntime()`: required
- `onPluginEnabled()`: optional, after runtime creation
- `onPluginDisabled()`: optional, before runtime termination

Invariant:

- subclasses must not override `onEnable()` or `onDisable()`
- command objects must not cache runtime instances

### `io.github.tetratheta.mol.plugin.PluginRuntime`

Purpose:

- owns Bukkit listeners and scheduled tasks for one runtime generation
- unregisters/cancels them in `terminate()`
- provides `runTask(...)` for next-tick synchronous work

Protected API:

- `getPlugin()`
- `registerListener(Listener)`
- `registerTask(BukkitTask)`
- `runTask(Runnable)`

Invariant:

- domain cleanup happens before `super.terminate()`
- listeners and tasks created by a runtime should be registered through this class

### `io.github.tetratheta.mol.plugin.LocalizedPluginRuntime<C extends BaseConfig>`

Purpose:

- creates a concrete config facade
- reads the configured language
- creates `MessageService`
- exposes `getConfig()` and `getMessageService()`
- saves config when validation changed it

Constructor pattern:

```java
super(plugin, ExampleConfig::new, ExampleConfig::getLanguage);
```

Invariant:

- subclass constructors should validate config, save fixes, create any runtime-scoped collaborators they need, then register listeners

### `io.github.tetratheta.mol.config.BaseConfig`

Purpose:

- wraps Bukkit `FileConfiguration`
- copies and reloads `config.yml`
- provides lazy default getters
- clamps numeric values when requested
- saves the config file with comment parsing enabled

Protected API:

- `getPlugin()`
- `getConfig()`

Invariant:

- getters may mutate the in-memory config
- persistence requires `saveConfig()`
- plugin-specific validation belongs in the concrete config class

### `io.github.tetratheta.mol.message.MessageService`

Purpose:

- copies bundled language resources into the plugin data folder
- loads editable and bundled language files
- resolves fallbacks
- formats messages with `MessageFormat`
- sends chat/action-bar messages
- logs localized info/warnings

Invariant:

- default constructor expects `languages/en.yml` and `languages/ko.yml`
- message placeholders use `MessageFormat`, not `{}` or string interpolation
- missing messages return the path itself

### `io.github.tetratheta.mol.message.MessageChannel`

Purpose:

- stable config values for message destinations
- parses aliases like `actionbar`, `action_bar`, and `action bar`

Invariant:

- unsupported values fall back to `ACTION_BAR`
- use `isSupportedConfigValue(...)` before logging or writing config fixes

## Expected Consumer Plugin Layout

Assume a plugin using `mol` has:

```text
src/main/java/.../ExamplePlugin.java
src/main/java/.../ExampleRuntime.java
src/main/java/.../config/ExampleConfig.java
src/main/resources/config.yml
src/main/resources/languages/en.yml
src/main/resources/languages/ko.yml
```

Do not add extra resource files unless the plugin actually supports those languages.

## Correct Bootstrap Pattern

Use this shape:

```java
public final class ExamplePlugin extends BasePlugin<ExampleRuntime> {
  @Override
  protected ExampleRuntime createRuntime() {
    return new ExampleRuntime(this);
  }

  @Override
  protected void onPluginEnabled() {
    registerCommand(() -> new ExampleCommand(this).getCommand());
  }
}
```

Avoid:

```java
@Override
public void onEnable() {
  // Do not override BasePlugin lifecycle methods.
}
```

Avoid caching:

```java
private final ExampleRuntime runtime;
```

Command classes should store the plugin, then call `plugin.getRuntime()` during command execution.

## Correct Runtime Pattern

Use this shape:

```java
public final class ExampleRuntime extends LocalizedPluginRuntime<ExampleConfig> {
  private final ExampleService service;

  public ExampleRuntime(ExamplePlugin plugin) {
    super(plugin, ExampleConfig::new, ExampleConfig::getLanguage);
    saveConfigIfChanged(getConfig().validateAndFix(getMessageService()));
    service = new ExampleService(getConfig(), getMessageService());
    registerListener(new ExampleListener(service, this::runTask));
  }

  public ExampleService getService() {
    return service;
  }
}
```

If the runtime does not use localization, extend `PluginRuntime` directly.

If the runtime owns recipes, files, caches, or other domain resources, override `terminate()`:

```java
@Override
public void terminate() {
  recipeService.unregisterRecipes();
  super.terminate();
}
```

Keep `super.terminate()` last.

## Config Rules

A concrete config class should expose domain-specific getters:

```java
public int getLimit() {
  return getInt("feature.limit", 10, 1, 100);
}
```

Use `getConfig()` only inside config classes or tightly-scoped command code that edits config values.

Validation should return a `boolean changed`:

```java
public boolean validateAndFix(MessageService messages) {
  boolean changed = false;
  String raw = getConfig().getString("mode", "default");
  if (!Mode.isSupported(raw)) {
    messages.logWarning("log.config.invalid-mode", raw);
    getConfig().set("mode", "default");
    changed = true;
  }
  return changed;
}
```

Then runtime code should call:

```java
saveConfigIfChanged(getConfig().validateAndFix(getMessageService()));
```

Do not hide validation side effects. If a config method changes values, make the method name say so: `validateAndFix`, `setLanguage`, `addWatchedRegion`, etc.

## Message Rules

Language files are YAML. Use message paths, not hardcoded text in commands or listeners.

Good:

```java
messages.send(sender, "command.reload.success");
messages.send(sender, "command.limit", limit);
```

Bad:

```java
sender.sendMessage("Reloaded.");
```

Use `MessageFormat` placeholders:

```yaml
command:
  limit: "Current limit: {0}"
```

Escape single quotes:

```yaml
example:
  text: "Don''t use raw single quotes with MessageFormat."
```

Lookup order is active language, active base language, fallback language, then path. When adding a new message key, update at least `en.yml` and `ko.yml`.

## Channel Rules

Use stable config values:

```yaml
notification:
  channel: "action-bar"
```

Config validation should normalize supported aliases:

```java
String configured = getConfig().getString("notification.channel", "action-bar");
MessageChannel channel = MessageChannel.fromConfig(configured);
if (MessageChannel.isSupportedConfigValue(configured)) {
  getConfig().set("notification.channel", channel.configValue());
  return !configured.equals(channel.configValue());
}
messages.logWarning("log.config.invalid-notification-channel", configured);
getConfig().set("notification.channel", MessageChannel.ACTION_BAR.configValue());
return true;
```

## Reload Safety

When handling a reload command:

```java
plugin.reloadRuntime();
plugin.getRuntime().getMessageService().send(sender, "command.reload.success");
```

The success message should be sent through the new runtime so it reflects the reloaded language.

Any listener that schedules delayed work should receive `this::runTask` or register its returned `BukkitTask`. This prevents stale scheduled callbacks after reload.

## Build and Publishing Facts

The Gradle build is a Java library build:

- group: `io.github.tetratheta`
- artifact: `mol`
- Java: 25
- Paper API: `compileOnly`
- publication: Maven Java with sources and Javadoc jars
- release/snapshot deployment: JReleaser

Safe verification commands:

```shell
./gradlew clean build javadoc
./gradlew publishAllPublicationsToMavenRepository
```

Unsafe until credentials are configured:

```shell
./gradlew jreleaserDeploy
./gradlew jreleaserFullRelease
```

Do not run Central deploy tasks just to validate source changes.

## Adoption Checklist for Consumer Plugins

When a plugin adopts `mol`:

1. Remove local duplicate `io.github.tetratheta.mol` sources from the plugin repository.
2. Add `compileOnly("io.github.tetratheta:mol:<version>")`.
3. Include `mol` in the shaded plugin jar.
4. Keep Paper API as `compileOnly`.
5. Extend `BasePlugin`.
6. Extend `LocalizedPluginRuntime` if the runtime uses config and messages.
7. Move command lifecycle registration to `registerCommand(...)`.
8. Keep plugin-specific behavior in the consumer plugin unless it has become a stable `mol` API.
9. Expose typed runtime getters for objects that commands need.
10. Run the plugin's existing compile/build task.

## Change Policy for AI Agents

Prefer these changes:

- small API additions that remove duplicated code in real consumers
- clearer JavaDoc when usage rules are implicit
- config/message helper improvements that preserve current behavior
- tests for pure Java behavior such as enum parsing

Avoid these changes:

- adding a service container
- introducing reflection-based registration
- adding a command framework
- adding dependencies for trivial helpers
- changing fallback defaults without updating docs and plugin configs
- making Bukkit/Paper runtime behavior depend on static mutable state

When unsure, inspect current consumer usage first, then choose the smallest shared abstraction that covers the repeated behavior.
