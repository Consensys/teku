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

package tech.pegasys.teku.datastructures.attestation;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;

public class DelayableAttestation {
  private final ValidateableAttestation validateableAttestation;
  private final Consumer<ValidateableAttestation> onSuccessfulProcessing;

  private volatile Optional<IndexedAttestation> maybeIndexedAttestation = Optional.empty();

  public DelayableAttestation(
      final ValidateableAttestation validateableAttestation,
      final Consumer<ValidateableAttestation> onSuccessfulProcessing) {
    this.validateableAttestation = validateableAttestation;
    this.onSuccessfulProcessing = onSuccessfulProcessing;
  }

  public Optional<IndexedAttestation> getIndexedAttestation() {
    return maybeIndexedAttestation;
  }

  public void setIndexedAttestation(IndexedAttestation indexedAttestation) {
    maybeIndexedAttestation = Optional.of(indexedAttestation);
  }

  public Attestation getAttestation() {
    return validateableAttestation.getAttestation();
  }

  public void onAttestationProcessedSuccessfully() {
    onSuccessfulProcessing.accept(validateableAttestation);
  }

  public UnsignedLong getEarliestSlotForForkChoiceProcessing() {
    return validateableAttestation.getAttestation().getEarliestSlotForForkChoiceProcessing();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return validateableAttestation.getAttestation().getDependentBlockRoots();
  }

  public Bytes32 hash_tree_root() {
    return validateableAttestation.getAttestation().hash_tree_root();
  }
}
