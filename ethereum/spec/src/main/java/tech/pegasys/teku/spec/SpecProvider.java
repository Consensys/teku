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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.base.Preconditions;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
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

  public Spec get(final UInt64 epoch) {
    return genesisSpec;
  }

  public Spec atSlot(final UInt64 slot) {
    return get(compute_epoch_at_slot(slot));
  }

  public Spec getGenesisSpec() {
    return get(UInt64.ZERO);
  }

  public SpecConstants getGenesisSpecConstants() {
    return getGenesisSpec().getConstants();
  }

  public ForkManifest getForkManifest() {
    return forkManifest;
  }

  public int slotsPerEpoch(final UInt64 epoch) {
    return get(epoch).getConstants().getSlotsPerEpoch();
  }

  public int secondsPerSlot(final UInt64 epoch) {
    return get(epoch).getConstants().getSecondsPerSlot();
  }

  public Bytes4 domainBeaconProposer(final UInt64 epoch) {
    return get(epoch).getConstants().getDomainBeaconProposer();
  }

  public Fork fork(final UInt64 epoch) {
    return forkManifest.get(epoch);
  }
}
