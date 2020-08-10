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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StubForkChoiceStrategy implements ForkChoiceStrategy {

  @Override
  public Bytes32 findHead(
      final MutableStore store,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final BeaconState justifiedCheckpointState) {
    return Bytes32.ZERO;
  }

  @Override
  public void onAttestation(MutableStore store, final IndexedAttestation attestation) {}

  @Override
  public void onBlock(final BeaconBlock block, final BeaconState state) {}

  @Override
  public Optional<UInt64> blockSlot(Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes32> blockParentRoot(Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public boolean contains(Bytes32 blockRoot) {
    return false;
  }

  @Override
  public void save() {}
}
