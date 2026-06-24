package io.github.tetratheta.mol.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
class MessageChannelTest {
  @Test
  void actionBarAliasesResolveToActionBar() {
    assertEquals(MessageChannel.ACTION_BAR, MessageChannel.fromConfig("action-bar"));
    assertEquals(MessageChannel.ACTION_BAR, MessageChannel.fromConfig("actionbar"));
    assertEquals(MessageChannel.ACTION_BAR, MessageChannel.fromConfig("action_bar"));
    assertEquals(MessageChannel.ACTION_BAR, MessageChannel.fromConfig("action bar"));
  }

  @Test
  void stableConfigValuesAreSupported() {
    assertEquals("chat", MessageChannel.CHAT.configValue());
    assertEquals("action-bar", MessageChannel.ACTION_BAR.configValue());
    assertTrue(MessageChannel.isSupportedConfigValue("chat"));
    assertTrue(MessageChannel.isSupportedConfigValue("action-bar"));
  }

  @Test
  void unsupportedValuesFallBackToActionBarButAreNotSupported() {
    assertEquals(MessageChannel.ACTION_BAR, MessageChannel.fromConfig("title"));
    assertFalse(MessageChannel.isSupportedConfigValue("title"));
    assertFalse(MessageChannel.isSupportedConfigValue(null));
  }
}
