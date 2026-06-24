package io.github.tetratheta.mol.plugin;

import io.github.tetratheta.mol.config.BaseConfig;
import io.github.tetratheta.mol.message.MessageService;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.plugin.java.JavaPlugin;

/// Runtime base class for plugins that use `BaseConfig` and `MessageService`.
///
/// Extend this class when the plugin constructor pattern is:
///
/// 1. create a config facade,
/// 2. read the configured language,
/// 3. create a localized message service,
/// 4. validate config and save fixes,
/// 5. create domain services and register listeners.
///
/// The subclass still owns domain service creation. This class only removes the repeated config/message wiring shared by CompactResources and
/// FarmManager.
///
/// Example:
/// ```java
/// public final class ExampleRuntime extends LocalizedPluginRuntime<ExampleConfig> {
///   public ExampleRuntime(ExamplePlugin plugin) {
///     super(plugin, ExampleConfig::new, ExampleConfig::getLanguage);
///     saveConfigIfChanged(getConfig().validateAndFix(getMessageService()));
///     registerListener(new ExampleListener(getMessageService()));
///   }
/// }
/// ```
///
/// @param <C> concrete plugin configuration facade
@SuppressWarnings("unused")
public abstract class LocalizedPluginRuntime<C extends BaseConfig> extends PluginRuntime {
  private final C config;
  private final MessageService messageService;

  /// Creates the shared config and localized message service for a runtime.
  ///
  /// `configFactory` is usually a constructor reference such as `ExampleConfig::new`. `languageProvider` should read the language from that config,
  /// commonly `ExampleConfig::getLanguage`. The plugin must include `languages/en.yml` and `languages/ko.yml` when the default `MessageService`
  /// constructor is sufficient.
  ///
  /// @param plugin           plugin that owns this runtime
  /// @param configFactory    creates the concrete config facade
  /// @param languageProvider reads the configured language from the config
  public LocalizedPluginRuntime(
      JavaPlugin plugin, Function<? super JavaPlugin, ? extends C> configFactory,
      Function<? super C, String> languageProvider
  ) {
    super(plugin);
    config = Objects.requireNonNull(configFactory, "configFactory").apply(plugin);
    String language = Objects.requireNonNull(languageProvider, "languageProvider").apply(config);
    messageService = new MessageService(plugin, language);
  }

  /// Returns the active configuration facade.
  ///
  /// @return active configuration facade
  public C getConfig() {
    return config;
  }

  /// Returns the active localized message service.
  ///
  /// @return localized message service
  public MessageService getMessageService() {
    return messageService;
  }

  /// Saves the active configuration when validation or migration changed it.
  ///
  /// This helper keeps runtime constructors linear: compute a `changed` flag from domain validation, pass it here, and continue creating services.
  /// The return value is the same flag for callers that want to log or branch afterward.
  ///
  /// @param changed true when the configuration should be persisted
  /// @return the provided `changed` value
  protected boolean saveConfigIfChanged(boolean changed) {
    if (changed) config.saveConfig();
    return changed;
  }
}
