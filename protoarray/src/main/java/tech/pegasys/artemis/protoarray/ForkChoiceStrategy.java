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

package tech.pegasys.artemis.protoarray;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.MutableStore;
import tech.pegasys.artemis.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;

public interface ForkChoiceStrategy {

  Bytes32 findHead(final ReadOnlyStore store);

  void onAttestation(final IndexedAttestation attestation);

  void onBlock(final ReadOnlyStore store, final BeaconBlock block);
}
