/*
 * Community Rust Plugin
 * Copyright (C) 2021-2025 Vladimir Shelkovnikov
 * mailto:community-rust AT pm DOT me
 * http://github.com/C4tWithShell/community-rust
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.elegoff.plugins.communityrust.language;

import org.elegoff.plugins.communityrust.settings.RustLanguageSettings;
import org.junit.jupiter.api.Test;
import org.sonar.api.config.internal.ConfigurationBridge;
import org.sonar.api.config.internal.MapSettings;


import static org.assertj.core.api.Assertions.assertThat;

class RustLanguageTest {
  @Test
  void test() {
    RustLanguage language = new RustLanguage(new ConfigurationBridge(new MapSettings()));
    assertThat(language.getKey()).isEqualTo("rust");
    assertThat(language.getName()).isEqualTo("Rust");
    assertThat(language.getFileSuffixes()).hasSize(1).contains(".rs");
  }

  @Test
  void custom_file_suffixes() {
    MapSettings settings = new MapSettings();
    settings.setProperty(RustLanguageSettings.FILE_SUFFIXES_KEY, "foo,bar");

    RustLanguage language = new RustLanguage(new ConfigurationBridge(settings));
    assertThat(language.getFileSuffixes()).hasSize(2).contains("foo");
  }
}
