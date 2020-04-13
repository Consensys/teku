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

package tech.pegasys.artemis.sync;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.Attestation;

public class DelayableAttestation {
  private final Attestation attestation;
  private final Runnable onSuccessfulProcessing;

  public DelayableAttestation(
      final Attestation attestation, final Runnable onSuccessfulProcessing) {
    this.attestation = attestation;
    this.onSuccessfulProcessing = onSuccessfulProcessing;
  }

  Attestation getAttestation() {
    return attestation;
  }

  void onAttestationProcessedSuccessfully() {
    onSuccessfulProcessing.run();
  }

  public UnsignedLong getEarliestSlotForForkChoiceProcessing() {
    return attestation.getEarliestSlotForForkChoiceProcessing();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return attestation.getDependentBlockRoots();
  }

  public Bytes32 hash_tree_root() {
    return attestation.hash_tree_root();
  }
}
