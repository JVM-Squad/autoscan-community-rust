/**
 * Community Rust Plugin
 * Copyright (C) 2021-2024 Vladimir Shelkovnikov
 * https://github.com/C4tWithShell/community-rust
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
package org.sonar.rust.parser.lexer;

import org.junit.jupiter.api.Test;
import org.sonar.rust.RustGrammar;


import static org.sonar.sslr.tests.Assertions.assertThat;

class SpacingTest {

  @Test
  void reallife() {
    assertThat(RustGrammar.create().build().rule(RustGrammar.SPC))
      .matches("/*foo*/")
      .matches("/*foo \n bar*/")
      .matches(" ")
      .matches("")
      .matches("\t")
      .matches("\r")
      .matches("\n")
      .matches("\r\n");
  }

}
