# mol

`mol` is a small Paper plugin utility library. It provides reusable building blocks for plugin runtime lifecycle, configuration access, localization,
and Paper command registration.

The library is intentionally narrow. It targets Paper plugins and does not try to be a standalone Java framework, Bukkit compatibility layer,
dependency injection container, or command framework.

## Requirements

- Java 25
- Paper API `26.1.2`
- Gradle 9.x

`mol` depends on Paper as `compileOnly`. Consumer plugins should compile against Paper and package `mol` into their final plugin jar.

## Installation

`mol` is published through Sonatype Central Portal:

```gradle
repositories {
  mavenCentral()
  maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
  implementation("io.github.tetratheta:mol:0.0.1")
}

jar {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
}
```

For a runnable Paper plugin jar, include `mol` in the output. Keep Paper itself as `compileOnly`.

## What `mol` Provides

- `BasePlugin<R extends PluginRuntime>`: a Paper `JavaPlugin` base class with final enable/disable handling, typed runtime access, runtime reload, and
  command lifecycle registration.
- `PluginRuntime`: tracks runtime-scoped Bukkit listeners and scheduled tasks, then cleans them up on reload or disable.
- `LocalizedPluginRuntime<C extends BaseConfig>`: creates the common `BaseConfig` and `MessageService` pair for plugins that use localized messages.
- `BaseConfig`: wraps Bukkit `config.yml` access with lazy defaults and range-clamped numeric getters.
- `MessageService`: copies bundled language files, loads editable language files, resolves fallbacks, formats messages, sends chat/action-bar
  messages, and logs localized messages.
- `MessageChannel`: parses stable config values such as `chat` and `action-bar`.

## Recommended Resource Layout

Plugins using the default localization setup should ship:

```text
src/main/resources/config.yml
src/main/resources/languages/en.yml
src/main/resources/languages/ko.yml
```

`config.yml` is copied by Bukkit through `saveDefaultConfig()` when a `BaseConfig` subclass is constructed.

Language files are copied by `MessageService` when the editable file does not exist in the plugin data folder. The editable file wins over the bundled
jar resource, so server owners can override only the messages they want.

## Plugin Bootstrap

Extend `BasePlugin` from the plugin entry point:

```java
package com.example.myplugin;

import io.github.tetratheta.mol.plugin.BasePlugin;

public final class MyPlugin extends BasePlugin<MyRuntime> {
  @Override
  protected MyRuntime createRuntime() {
    return new MyRuntime(this);
  }

  @Override
  protected void onPluginEnabled() {
    registerCommand(() -> new MyCommand(this).getCommand());
  }
}
```

`BasePlugin` owns `onEnable()` and `onDisable()`. Do not override those methods in subclasses. Use `createRuntime()`, `onPluginEnabled()`, and
`onPluginDisabled()` instead.

Commands should call `plugin.getRuntime()` when they execute. Do not cache a runtime in a command object, because `reloadRuntime()` replaces the
runtime instance.

## Runtime Pattern

Use `LocalizedPluginRuntime` when a plugin has a `BaseConfig` subclass and localized messages:

```java
package com.example.myplugin;

import io.github.tetratheta.mol.plugin.LocalizedPluginRuntime;

public final class MyRuntime extends LocalizedPluginRuntime<MyConfig> {
  private final MyFeature feature;

  public MyRuntime(MyPlugin plugin) {
    super(plugin, MyConfig::new, MyConfig::getLanguage);
    saveConfigIfChanged(getConfig().validateAndFix(getMessageService()));
    feature = new MyFeature(getConfig(), getMessageService());
    registerListener(new MyListener(feature, this::runTask));
  }

  public MyFeature getFeature() {
    return feature;
  }
}
```

Create runtime-scoped collaborators after config validation. Register listeners through `registerListener(...)`. Use `runTask(...)` for one-tick
synchronous callbacks. Use `registerTask(...)` for tasks created directly with Bukkit's scheduler.

Override `terminate()` only when the runtime owns resources that need cleanup:

```java

@Override
public void terminate() {
  feature.close();
  super.terminate();
}
```

Call `super.terminate()` last so plugin-owned resources stop before Bukkit listeners and tasks are released.

## Configuration

Create a concrete config class:

```java
package com.example.myplugin;

import io.github.tetratheta.mol.config.BaseConfig;
import io.github.tetratheta.mol.message.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyConfig extends BaseConfig {
  public MyConfig(JavaPlugin plugin) {
    super(plugin);
  }

  public String getLanguage() {
    return getString("language", MessageService.defaultLanguage());
  }

  public int getLimit() {
    return getInt("feature.limit", 10, 1, 100);
  }

  public boolean validateAndFix(MessageService messages) {
    boolean changed = false;
    if (getLimit() < 5) {
      messages.logWarning("log.config.limit-too-low");
      changed = true;
    }
    return changed;
  }
}
```

Primitive getters write defaults lazily when a path is missing. Range-aware numeric getters clamp values in memory. Call `saveConfig()` directly, or
`saveConfigIfChanged(...)` from `LocalizedPluginRuntime`, after validation or migration code changes values.

Use `getConfig()` in the concrete config class for custom sections. Keep plugin-specific validation in the concrete config class, because only the
consumer plugin knows whether an invalid value should be removed, clamped, logged, or preserved.

## Messages

Messages are YAML paths loaded from language files:

```yaml
command:
  reload:
    success: "Configuration reloaded."
  limit: "Current limit: {0}"
log:
  config:
    limit-too-low: "The configured limit is too low."
```

Use positional `MessageFormat` placeholders such as `{0}` and `{1}`. Escape literal single quotes by doubling them:

```yaml
example:
  quote: "Don''t forget MessageFormat escaping."
```

Send messages through `MessageService`:

```java
getMessageService().send(sender, "command.reload.success");
getMessageService().send(sender, "command.limit",getConfig().getLimit());
getMessageService().send(sender, MessageChannel.ACTION_BAR, "command.limit",getConfig().getLimit());
```

Action-bar messages are sent only to players. Console and other non-player senders fall back to normal chat.

Language lookup order is:

1. editable active language file
2. bundled active language resource
3. editable and bundled base language, such as `ko` for `ko_kr`
4. fallback language, normally `en`
5. the message path itself

## Reload Behavior

`reloadRuntime()`:

1. terminates the old runtime,
2. reloads Bukkit config from disk,
3. creates a new runtime.

Listeners registered through `PluginRuntime` are unregistered. Tasks registered through `PluginRuntime` are cancelled. This prevents old listeners and
delayed tasks from touching stale runtime state after a reload.

## Packaging in a Paper Plugin

A typical consumer plugin keeps Paper provided by the server, then uses a dedicated `shade` configuration for libraries that must be bundled into the plugin jar:

```gradle
plugins {
  id 'java'
  id 'com.gradleup.shadow' version '9.0.0'
}

configurations {
  shade
  compileOnly.extendsFrom(shade)
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
  shade("io.github.tetratheta:mol:0.0.1")
}

tasks.named('shadowJar') {
  archiveClassifier.set('')
  configurations = [project.configurations.shade]
}
```

Use `implementation("io.github.tetratheta:mol:0.0.1")` instead only when the plugin's build already shades the `implementation` runtime classpath. Do not declare both `compileOnly` and `implementation` for `mol`; that is redundant. The important rule is that Paper stays provided by the server, while `mol` is available inside the plugin jar at runtime.

## Publishing `mol`

The project publishes a normal Java library with sources and Javadoc jars.

Local build:

```shell
./gradlew clean build javadoc
```

Local staging publication:

```shell
./gradlew publishAllPublicationsToMavenRepository
```

The staging repository is written to:

```text
build/staging-deploy
```

JReleaser configuration is present for Sonatype Central Portal release deployment and Sonatype Central Snapshots deployment, but deploy tasks require
PGP and Central credentials. Do not run `jreleaserDeploy` or `jreleaserFullRelease` until those are configured.

## Adoption Checklist

For a plugin adopting `mol`:

1. Add the `mol` dependency.
2. Include `mol` in the final plugin jar.
3. Keep Paper API as `compileOnly`.
4. Add `config.yml` and language files if using `BaseConfig` and `MessageService`.
5. Make the main plugin class extend `BasePlugin`.
6. Make the runtime extend `LocalizedPluginRuntime` when it uses config and messages.
7. Register Paper command nodes with `registerCommand(...)`.
8. Keep runtime access typed through the plugin's runtime class.

## Non-Goals

- No general dependency injection container.
- No Bukkit-only support.
- No non-Paper runtime support.
- No command framework beyond Paper lifecycle command registration.
- No backward compatibility guarantee before the API is declared stable.
