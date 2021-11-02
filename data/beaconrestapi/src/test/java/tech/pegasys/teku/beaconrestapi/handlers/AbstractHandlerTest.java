/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beaconrestapi.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbstractHandlerTest {

  @Test
  void shouldHandleParameterAtEnd() {
    assertThat(AbstractHandler.routeWithBracedParameters("/foo/:bar")).isEqualTo("/foo/{bar}");
  }

  @Test
  void shouldHandleParameterInMiddle() {
    assertThat(AbstractHandler.routeWithBracedParameters("/:foo/bar")).isEqualTo("/{foo}/bar");
  }

  @Test
  void shouldHandleUnderscore() {
    assertThat(AbstractHandler.routeWithBracedParameters("/foo/:bar_BAR"))
        .isEqualTo("/foo/{bar_BAR}");
  }

  @Test
  void shouldHandleMixedCase() {
    assertThat(AbstractHandler.routeWithBracedParameters("/foo/:bAr")).isEqualTo("/foo/{bAr}");
  }

  @Test
  void shouldHandleMultipleReplacements() {
    assertThat(AbstractHandler.routeWithBracedParameters("/:foo/:bar/:FOO/BAR"))
        .isEqualTo("/{foo}/{bar}/{FOO}/BAR");
  }
}
