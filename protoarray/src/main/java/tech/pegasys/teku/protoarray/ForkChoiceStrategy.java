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

package tech.pegasys.teku.protoarray;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface ForkChoiceStrategy extends ReadOnlyForkChoiceStrategy {

  Bytes32 findHead(
      VoteUpdater store,
      List<ProposerWeighting> removedProposerWeightings,
      Checkpoint finalizedCheckpoint,
      Checkpoint justifiedCheckpoint,
      BeaconState justifiedCheckpointState);

  void onAttestation(VoteUpdater store, IndexedAttestation attestation);
}
