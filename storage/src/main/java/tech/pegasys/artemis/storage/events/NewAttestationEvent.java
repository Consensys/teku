/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage.events;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.LatestMessage;

public class NewAttestationEvent {
  private final BeaconStateWithCache state;
  private final Checkpoint checkpoint;
  private final List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages;

  public NewAttestationEvent(
      final BeaconStateWithCache state,
      final Checkpoint checkpoint,
      final List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages) {
    this.state = state;
    this.checkpoint = checkpoint;
    this.attesterLatestMessages = attesterLatestMessages;
  }

  public BeaconStateWithCache getState() {
    return state;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public List<Pair<UnsignedLong, LatestMessage>> getAttesterLatestMessages() {
    return attesterLatestMessages;
  }
}
