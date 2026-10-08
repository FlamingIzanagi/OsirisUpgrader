package com.osiris.upgrader.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMergerTest {
    @Test
    void addsAMissingKeyWithoutReplacingTheExistingOne() {
        String text = """
                messages:
                  prefix: "&8[Custom]"
                  reload-success: "&aListo."

                # Menús
                gui-texts:
                  none: "Nada"
                """;
        String fragment = YamlMerger.fragment("daily-spin", "&eHola", 2);
        String updated = YamlMerger.insert(text, List.of(new YamlMerger.Placement("messages", fragment)));

        assertTrue(updated.contains("prefix: \"&8[Custom]\""));
        assertTrue(updated.contains("reload-success: \"&aListo.\""));
        assertTrue(updated.contains("  daily-spin: '&eHola'"));
        assertTrue(updated.indexOf("daily-spin") < updated.indexOf("# Menús"));
        assertTrue(updated.indexOf("gui-texts:") > updated.indexOf("daily-spin"));
    }

    @Test
    void addsANestedListAndKeepsComments() {
        String text = """
                gui-texts:
                  # valor del premio
                  target-selected:
                    - "&7Vale"
                """;
        String fragment = YamlMerger.fragment("limited-badge", List.of("&6&lPremio Limitado"), 2);
        String updated = YamlMerger.insert(text, List.of(new YamlMerger.Placement("gui-texts", fragment)));

        assertTrue(updated.contains("# valor del premio"));
        assertTrue(updated.contains("target-selected:"));
        assertTrue(updated.contains("  limited-badge:"));
        assertTrue(updated.contains("    - '&6&lPremio Limitado'"));
    }

    @Test
    void appendsANewRootSection() {
        String text = "menu-type: vanilla\n";
        String fragment = YamlMerger.fragment("sounds", Map.of("click", "UI_BUTTON_CLICK"), 0);
        String updated = YamlMerger.insert(text, List.of(new YamlMerger.Placement("", fragment)));

        assertTrue(updated.startsWith("menu-type: vanilla\n"));
        assertTrue(updated.contains("sounds:\n  click: 'UI_BUTTON_CLICK'\n"));
    }

    @Test
    void reportsWhenTheParentSectionIsMissing() {
        assertEquals(false, YamlMerger.canPlace("menu-type: vanilla\n", "sounds"));
        assertEquals(0, YamlMerger.indentOfPath("messages:\n  prefix: a\n", "messages"));
    }
}
