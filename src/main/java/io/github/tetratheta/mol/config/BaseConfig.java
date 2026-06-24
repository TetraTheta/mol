package io.github.tetratheta.mol.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/// Provides convenience accessors for a Paper plugin's `config.yml`.
///
/// Put the static defaults that should ship with the plugin in `src/main/resources/config.yml`. The constructor calls
/// `JavaPlugin#saveDefaultConfig()` and `JavaPlugin#reloadConfig()`, so Bukkit copies that resource to the plugin data folder the first time the
/// plugin starts. Comments from the bundled file are kept by Bukkit when they are loaded with `parseComments(true)`.
///
/// Dynamic defaults belong in typed getter calls. For example, `getInt("feature.limit", 10, 1, 100)` writes `feature.limit: 10` only when the path is
/// missing, then returns the stored value after clamping. Call `saveConfig()` after validation or migration code changes values in memory.
///
/// Example:
/// ```java
/// public final class ExampleConfig extends BaseConfig {
///   public ExampleConfig(JavaPlugin plugin) {
///     super(plugin);
///   }
///
///   public String getLanguage() {
///     return getString("language", "ko");
///   }
/// }
/// ```
public abstract class BaseConfig {
  private final File configPath;
  private final Logger logger;
  private final JavaPlugin plugin;
  private FileConfiguration config;

  /// Prepares the plugin configuration file and enables comment-aware saves.
  ///
  /// The provided plugin must have a `config.yml` resource when the plugin relies on bundled defaults. Without that file Bukkit still creates an
  /// in-memory configuration, but there is nothing for `saveDefaultConfig()` to copy into the data folder.
  ///
  /// @param provided plugin instance that owns `config.yml`
  public BaseConfig(JavaPlugin provided) {
    plugin = provided;
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    logger = plugin.getLogger();
    config = plugin.getConfig();
    config.options().parseComments(true);
    config.options().copyDefaults(false);
    configPath = new File(plugin.getDataFolder(), "config.yml");
  }

  /// Returns the plugin that owns this configuration facade.
  ///
  /// Use this when validation code needs plugin services such as the logger or data folder. Domain services should receive explicit values instead of
  /// reaching through the plugin when possible.
  ///
  /// @return owning plugin
  protected JavaPlugin getPlugin() {
    return plugin;
  }

  /// Returns the live Bukkit configuration object.
  ///
  /// Use this for domain-specific sections that are too structured for the primitive convenience getters. Values changed through the returned object
  /// are not persisted until `saveConfig()` is called.
  ///
  /// @return live plugin configuration
  protected FileConfiguration getConfig() {
    return config;
  }

  /// Returns a boolean value and writes the default when the path is missing.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @return configured value or `def`
  public boolean getBoolean(String path, boolean def) {
    if (config.isSet(path)) return config.getBoolean(path, def);
    config.set(path, def);
    return def;
  }

  /// Returns a double clamped to the provided inclusive range.
  ///
  /// Missing paths are written with `def`. Existing values outside the range are written back as the nearest boundary so later reads see the
  /// normalized value.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @param min  minimum accepted value
  /// @param max  maximum accepted value
  /// @return normalized configured value
  public double getDouble(String path, double def, double min, double max) {
    if (!config.isSet(path)) {
      config.set(path, def);
      return def;
    }
    double value = config.getDouble(path, def);
    if (value > max) {
      config.set(path, max);
      return max;
    }
    if (value < min) {
      config.set(path, min);
      return min;
    }
    return value;
  }

  /// Returns a double clamped to `0` through `100`.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @return normalized configured value
  public double getDouble(String path, double def) {
    return getDouble(path, def, 0, 100);
  }

  /// Returns an integer clamped to the provided inclusive range.
  ///
  /// Missing paths are written with `def`. Existing values outside the range are written back as the nearest boundary so later reads see the
  /// normalized value.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @param min  minimum accepted value
  /// @param max  maximum accepted value
  /// @return normalized configured value
  public int getInt(String path, int def, int min, int max) {
    if (!config.isSet(path)) {
      config.set(path, def);
      return def;
    }
    int value = config.getInt(path, def);
    if (value > max) {
      config.set(path, max);
      return max;
    }
    if (value < min) {
      config.set(path, min);
      return min;
    }
    return value;
  }

  /// Returns an integer clamped to `0` through `100`.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @return normalized configured value
  public int getInt(String path, int def) {
    return getInt(path, def, 0, 100);
  }

  /// Returns a string list and writes the default when the path is missing.
  ///
  /// Bukkit returns an empty list when a path exists but is not a list. Keep domain validation in the concrete config class when that distinction
  /// matters.
  ///
  /// @param path configuration path
  /// @param def  default list stored when the path is missing
  /// @return configured string list
  public List<String> getStringList(String path, List<String> def) {
    if (config.isSet(path)) return config.getStringList(path);
    config.set(path, def);
    return def;
  }

  /// Returns a long clamped to the provided inclusive range.
  ///
  /// Missing paths are written with `def`. Existing values outside the range are written back as the nearest boundary so later reads see the
  /// normalized value.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @param min  minimum accepted value
  /// @param max  maximum accepted value
  /// @return normalized configured value
  public long getLong(String path, long def, long min, long max) {
    if (!config.isSet(path)) {
      config.set(path, def);
      return def;
    }
    long value = config.getLong(path, def);
    if (value > max) {
      config.set(path, max);
      return max;
    }
    if (value < min) {
      config.set(path, min);
      return min;
    }
    return value;
  }

  /// Returns a long clamped to `0` through `100`.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @return normalized configured value
  public long getLong(String path, long def) {
    return getLong(path, def, 0, 100);
  }

  /// Returns a string and writes the default when the path is missing.
  ///
  /// @param path configuration path
  /// @param def  default value stored when the path is missing
  /// @return configured string or `def`
  public String getString(String path, String def) {
    if (config.isSet(path)) return config.getString(path, def);
    config.set(path, def);
    return def;
  }

  /// Saves the current in-memory configuration to `config.yml`.
  ///
  /// Call this after validation, migration, or command handlers change values. Getter-created defaults are also only persisted when this method
  /// runs.
  public void saveConfig() {
    try {
      config.options().parseComments(true);
      config.save(configPath);
      config = plugin.getConfig();
      config.options().parseComments(true);
    } catch (IOException e) {
      logger.severe("Failed to save configuration file! - " + e.getLocalizedMessage());
    }
  }
}
