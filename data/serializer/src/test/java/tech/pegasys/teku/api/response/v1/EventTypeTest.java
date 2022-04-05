/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;

public class EventTypeTest {
  private final OpenApiTestUtil<EventTypeTest> util = new OpenApiTestUtil<>(EventTypeTest.class);

  @Test
  void shouldParseEventTypes() {
    List<EventType> topics =
        EventType.getTopics(List.of("head", "chain_reorg", "finalized_checkpoint"));
    assertThat(topics)
        .containsExactlyInAnyOrder(
            EventType.head, EventType.chain_reorg, EventType.finalized_checkpoint);
  }

  @Test
  void shouldFailToParseInvalidEvents() {
    assertThrows(IllegalArgumentException.class, () -> EventType.getTopics(List.of("head1")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getEventTypes")
  @SuppressWarnings("rawtypes")
  void shouldHaveConsistentEventStructures(final String name, final Class clazz)
      throws IOException {
    util.compareToKnownDefinition(clazz);
  }

  public static Stream<Arguments> getEventTypes() {
    // attestation, voluntary_exit, contributionAndProof are all the standard objects,
    // but the below objects don't have schema definition checks anywhere else
    return Stream.of(
        Arguments.of("BlockEvent", BlockEvent.class),
        Arguments.of("ChainReorgEvent", ChainReorgEvent.class),
        Arguments.of("FinalizedCheckpointEvent", FinalizedCheckpointEvent.class),
        Arguments.of("HeadEvent", HeadEvent.class),
        Arguments.of("SyncStateChangeEvent", SyncStateChangeEvent.class));
  }
}
