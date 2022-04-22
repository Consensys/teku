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
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.SSZ_OR_JSON_CONTENT_TYPES;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.APPLICATION_JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.APPLICATION_OCTET_STREAM;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbstractHandlerTest {

  private static final List<String> JSON_ONLY = List.of(APPLICATION_JSON);

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

  @Test
  void accept_shouldReturnJsonIfNotSpecified() {
    assertThat(AbstractHandler.getContentType(SSZ_OR_JSON_CONTENT_TYPES, Optional.empty()))
        .isEqualTo(APPLICATION_JSON);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "application/octet-stream;q=1",
        "a/b/c/d;q=1",
        "application/js;q=abc",
        ";q=1",
        "a;q=1234",
        ";qabc",
        ",,,",
        ";q=1;q=1;q=1",
        ";;;"
      })
  void accept_errorsAgainstJsonHeaderRequirement(final String invalidMimeString) {
    assertThat(AbstractHandler.getContentType(JSON_ONLY, Optional.of(invalidMimeString)))
        .isEqualTo(APPLICATION_JSON);
  }

  @Test
  void accept_shouldPreferenceFirstSpecified() {
    assertThat(
            AbstractHandler.getContentType(
                SSZ_OR_JSON_CONTENT_TYPES,
                Optional.of("application/octet-stream,application/json;q=0.9")))
        .isEqualTo(APPLICATION_OCTET_STREAM);
  }

  @Test
  void accept_shouldPreferenceHighestQValue() {
    assertThat(
            AbstractHandler.getContentType(
                SSZ_OR_JSON_CONTENT_TYPES,
                Optional.of("application/octet-stream;q=0.1,application/json;q=0.9")))
        .isEqualTo(APPLICATION_JSON);
  }

  @Test
  void accept_shouldHandleSingleArgWithQSet() {
    assertThat(
            AbstractHandler.getContentType(
                SSZ_OR_JSON_CONTENT_TYPES, Optional.of("application/octet-stream;q=1.0")))
        .isEqualTo(APPLICATION_OCTET_STREAM);
  }
}
