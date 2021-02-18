/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class SpecProvider {
  // Eventually we will have multiple versioned specs, where each version is active for a specific
  // range of epochs
  private final Spec genesisSpec;
  private final ForkManifest forkManifest;

  private SpecProvider(final Spec genesisSpec, final ForkManifest forkManifest) {
    Preconditions.checkArgument(forkManifest != null);
    Preconditions.checkArgument(genesisSpec != null);
    this.genesisSpec = genesisSpec;
    this.forkManifest = forkManifest;
  }

  private SpecProvider(final Spec genesisSpec) {
    this(genesisSpec, ForkManifest.create(genesisSpec.getConstants()));
  }

  public static SpecProvider create(final SpecConfiguration config) {
    final Spec initialSpec = new Spec(config.constants());
    return new SpecProvider(initialSpec);
  }

  public static SpecProvider create(
      final SpecConfiguration config, final ForkManifest forkManifest) {
    final Spec initialSpec = new Spec(config.constants());
    return new SpecProvider(initialSpec, forkManifest);
  }

  public Spec atEpoch(final UInt64 epoch) {
    return genesisSpec;
  }

  public Spec atSlot(final UInt64 slot) {
    // Calculate using the latest spec
    final UInt64 epoch = getLatestSpec().getBeaconStateUtil().computeEpochAtSlot(slot);
    return atEpoch(epoch);
  }

  public BeaconStateUtil getBeaconStateUtil(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil();
  }

  public Spec getGenesisSpec() {
    return atEpoch(UInt64.ZERO);
  }

  public SpecConstants getGenesisSpecConstants() {
    return getGenesisSpec().getConstants();
  }

  public ForkManifest getForkManifest() {
    return forkManifest;
  }

  public int slotsPerEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getConstants().getSlotsPerEpoch();
  }

  public int secondsPerSlot(final UInt64 epoch) {
    return atEpoch(epoch).getConstants().getSecondsPerSlot();
  }

  public UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getBeaconStateUtil().computeStartSlotAtEpoch(epoch);
  }

  public Bytes4 domainBeaconProposer(final UInt64 epoch) {
    return atEpoch(epoch).getConstants().getDomainBeaconProposer();
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil().computeEpochAtSlot(slot);
  }

  public Fork fork(final UInt64 epoch) {
    return forkManifest.get(epoch);
  }

  // Validator Utils
  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return atState(state).getValidatorsUtil().getMaxLookaheadEpoch(state);
  }

  public List<Integer> getActiveValidatorIndices(final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch).getValidatorsUtil().getActiveValidatorIndices(state, epoch);
  }

  public int countActiveValidators(final BeaconState state, final UInt64 epoch) {
    return getActiveValidatorIndices(state, epoch).size();
  }

  public Optional<BLSPublicKey> getValidatorPubKey(
      final BeaconState state, final UInt64 proposerIndex) {
    return atState(state).getValidatorsUtil().getValidatorPubKey(state, proposerIndex);
  }

  // Private helpers
  private Spec atState(final BeaconState state) {
    return atSlot(state.getSlot());
  }

  private Spec getLatestSpec() {
    // When fork manifest is non-empty, we should pull the newest spec here
    return genesisSpec;
  }

  public long getSlotsPerHistoricalRoot(final UInt64 slot) {
    return atSlot(slot).getConstants().getSlotsPerHistoricalRoot();
  }

  public int getSlotsPerEpoch(final UInt64 slot) {
    return atSlot(slot).getConstants().getSlotsPerEpoch();
  }

  public int getSecondsPerSlot(final UInt64 slot) {
    return atSlot(slot).getConstants().getSecondsPerSlot();
  }

  public UInt64 getCurrentEpoch(final BeaconState state) {
    return atSlot(state.getSlot()).getBeaconStateUtil().getCurrentEpoch(state);
  }
}
