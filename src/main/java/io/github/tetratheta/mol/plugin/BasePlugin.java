package io.github.tetratheta.mol.plugin;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

/// Base class for Paper plugins that rebuild services as a single runtime object.
///
/// Subclasses provide a concrete runtime through `createRuntime()`. They can register Paper lifecycle commands in `onPluginEnabled()` by calling
/// `registerCommand(Supplier)`.
///
/// Example:
/// ```java
/// public final class ExamplePlugin extends BasePlugin<ExampleRuntime> {
///   @Override
///   protected ExampleRuntime createRuntime() {
///     return new ExampleRuntime(this);
///   }
///
///   @Override
///   protected void onPluginEnabled() {
///     registerCommand(() -> new ExampleCommand(this).getCommand());
///   }
/// }
/// ```
///
/// @param <R> concrete runtime type exposed to commands and listeners
@SuppressWarnings("unused")
public abstract class BasePlugin<R extends PluginRuntime> extends JavaPlugin {
  private R runtime;

  /// Creates the runtime and then runs subclass startup hooks.
  ///
  /// Bukkit calls this method when the plugin is enabled. Subclasses should use `createRuntime()` and `onPluginEnabled()` instead of overriding this
  /// method.
  @Override
  public final void onEnable() {
    runtime = createRuntime();
    onPluginEnabled();
  }

  /// Runs subclass shutdown hooks and releases the active runtime.
  ///
  /// Bukkit calls this method when the plugin is disabled. Subclasses should use `onPluginDisabled()` and runtime `terminate()` overrides instead of
  /// overriding this method.
  @Override
  public final void onDisable() {
    onPluginDisabled();
    if (runtime != null) {
      runtime.terminate();
      runtime = null;
    }
  }

  /// Creates a new runtime from the plugin's current configuration state.
  ///
  /// `BasePlugin` calls this during plugin enable and `reloadRuntime()`.
  ///
  /// @return new runtime
  protected abstract R createRuntime();

  /// Runs after the initial runtime is created.
  ///
  /// Use this hook for startup work that needs an active runtime, such as Paper lifecycle command registration.
  protected void onPluginEnabled() {}

  /// Runs before the active runtime is terminated.
  ///
  /// Use this hook for plugin-level shutdown work that is separate from runtime resource cleanup.
  protected void onPluginDisabled() {}

  /// Registers a Brigadier command node during Paper's command lifecycle event.
  ///
  /// Call this from `onPluginEnabled()`. The supplier is evaluated inside the lifecycle callback, so command builders can safely capture `this` and
  /// later call `getRuntime()` when a command executes.
  ///
  /// @param commandFactory creates the command node to register
  protected final void registerCommand(Supplier<LiteralCommandNode<CommandSourceStack>> commandFactory) {
    getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(commandFactory.get()));
  }

  /// Returns the active runtime, creating one only when the plugin has no live runtime.
  ///
  /// Commands should call this at execution time rather than caching the runtime, because `reloadRuntime()` replaces the runtime object.
  ///
  /// @return active runtime
  public R getRuntime() {
    if (runtime == null) runtime = createRuntime();
    return runtime;
  }

  /// Rebuilds the active runtime from the latest disk configuration.
  ///
  /// The old runtime is terminated first, which unregisters listeners and cancels tracked tasks. The plugin config is then reloaded from disk before
  /// a new runtime is created.
  public void reloadRuntime() {
    if (runtime != null) runtime.terminate();
    reloadConfig();
    runtime = createRuntime();
  }
}
