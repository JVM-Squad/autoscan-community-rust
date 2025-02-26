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
package org.sonar.rust.parser.items;

import org.junit.jupiter.api.Test;
import org.sonar.rust.RustGrammar;


import static org.sonar.sslr.tests.Assertions.assertThat;

class ConstantTest {

  @Test
  void testConstant() {
    assertThat(RustGrammar.create().build().rule(RustGrammar.CONSTANT_ITEM))
      .matches("const BIT1: u32 = 1 << 0;")
      .matches("const BIT2: u32 = 1 << 1;")
      .matches("const BITS: [u32; 2] = [BIT1, BIT2];")
      .matches("const STRING: &'static str = \"bitstring\";")
      .matches("const WHITE: Color = Color(255, 255, 255);")
      .matches("const MAX: Self;")
      .matches("const EMPTY: Vec<i32> = Vec::new();")
    ;

  }
}
