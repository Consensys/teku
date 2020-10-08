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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

public class EventTypeTest {

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
}
