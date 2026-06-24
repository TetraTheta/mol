package io.github.tetratheta.mol.message;

import java.util.Locale;

/// Represents where localized player-facing messages are shown.
///
/// Use the stable value returned by `configValue()` in plugin `config.yml` files. `fromConfig(String)` accepts common variants such as `actionbar`,
/// `action_bar`, and `action bar`, then normalizes them to `ACTION_BAR`.
@SuppressWarnings("unused")
public enum MessageChannel {
  /// Sends the message through normal chat.
  CHAT("chat"),
  /// Sends the message to the player's action bar when the recipient is a player.
  ACTION_BAR("action-bar");
  //
  private final String configValue;

  /// Stores the stable configuration spelling for the enum value.
  ///
  /// @param configValue stable value written to config files
  MessageChannel(String configValue) {
    this.configValue = configValue;
  }

  /// Resolves a configured value into a message channel.
  ///
  /// Unsupported values fall back to `ACTION_BAR` because both CompactResources and FarmManager use action-bar style notifications as their quiet
  /// default. Validate first with `isSupportedConfigValue(String)` when invalid user input should be logged or written back.
  ///
  /// @param value configured value
  /// @return matching channel, or `ACTION_BAR` when the value is unsupported
  public static MessageChannel fromConfig(String value) {
    if (value == null) return ACTION_BAR;
    String normalized = normalizeConfigValue(value);
    if ("actionbar".equals(normalized)) return ACTION_BAR;
    for (MessageChannel channel : values()) {
      if (channel.configValue.equals(normalized)) return channel;
    }
    return ACTION_BAR;
  }

  /// Returns whether the raw value clearly means a supported message channel.
  ///
  /// Use this during config validation before overwriting the configured value. It shares the same aliases as `fromConfig(String)` but does not apply
  /// that method's fallback.
  ///
  /// @param value configured value
  /// @return true when the value is a valid channel or alias
  public static boolean isSupportedConfigValue(String value) {
    if (value == null) return false;
    String normalized = normalizeConfigValue(value);
    if ("actionbar".equals(normalized)) return true;
    for (MessageChannel channel : values()) {
      if (channel.configValue.equals(normalized)) return true;
    }
    return false;
  }

  /// Normalizes free-form configuration input before matching aliases.
  ///
  /// Hyphen is the canonical separator because it reads well in YAML and avoids enum-name coupling in user configuration.
  ///
  /// @param value configured value
  /// @return normalized value
  private static String normalizeConfigValue(String value) {
    return value.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
  }

  /// Returns the stable configuration value for this channel.
  ///
  /// @return stable configuration value
  public String configValue() {
    return configValue;
  }
}
