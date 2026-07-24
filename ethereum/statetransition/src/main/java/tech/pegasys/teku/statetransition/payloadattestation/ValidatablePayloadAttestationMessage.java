/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.payloadattestation;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatablePayloadAttestationMessage {

  private final PayloadAttestationMessage message;

  private volatile Optional<IntSet> ptcPositions = Optional.empty();

  private ValidatablePayloadAttestationMessage(final PayloadAttestationMessage message) {
    this.message = message;
  }

  public static ValidatablePayloadAttestationMessage fromValidator(
      final PayloadAttestationMessage message) {
    return new ValidatablePayloadAttestationMessage(message);
  }

  public static ValidatablePayloadAttestationMessage fromNetwork(
      final PayloadAttestationMessage message) {
    return new ValidatablePayloadAttestationMessage(message);
  }

  public static ValidatablePayloadAttestationMessage fromBlock(
      final PayloadAttestationMessage message) {
    return new ValidatablePayloadAttestationMessage(message);
  }

  public PayloadAttestationMessage getMessage() {
    return message;
  }

  public PayloadAttestationData getData() {
    return message.getData();
  }

  public UInt64 getValidatorIndex() {
    return message.getValidatorIndex();
  }

  public Optional<IntSet> getPtcPositions() {
    return ptcPositions;
  }

  public IntSet calculatePtcPositions(final Spec spec, final BeaconState state) {
    final Optional<IntSet> currentValue = ptcPositions;
    if (currentValue.isPresent()) {
      return currentValue.get();
    }

    final int validatorIndex = getValidatorIndex().intValue();
    final IntList ptc = spec.getPtc(state, getData().getSlot());
    final IntSet positions = new IntOpenHashSet();
    for (int i = 0; i < ptc.size(); i++) {
      if (ptc.getInt(i) == validatorIndex) {
        positions.add(i);
      }
    }

    final IntSet immutablePositions = IntSets.unmodifiable(positions);
    this.ptcPositions = Optional.of(immutablePositions);
    return immutablePositions;
  }

  @VisibleForTesting
  void setPtcPositions(final IntSet ptcPositions) {
    this.ptcPositions = Optional.of(IntSets.unmodifiable(new IntOpenHashSet(ptcPositions)));
  }
}
