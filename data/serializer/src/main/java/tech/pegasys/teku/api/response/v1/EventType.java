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

import java.util.List;
import java.util.stream.Collectors;

public enum EventType {
  head,
  block,
  attestation,
  voluntary_exit,
  finalized_checkpoint,
  chain_reorg,
  sync_state;

  public static List<EventType> getTopics(List<String> topics) {
    return topics.stream().map(EventType::valueOf).collect(Collectors.toList());
  }
}
