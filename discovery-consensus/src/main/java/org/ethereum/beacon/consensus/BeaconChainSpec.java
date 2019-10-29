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

package org.ethereum.beacon.consensus;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.consensus.hasher.SSZObjectHasher;
import org.ethereum.beacon.consensus.spec.BlockProcessing;
import org.ethereum.beacon.consensus.spec.EpochProcessing;
import org.ethereum.beacon.consensus.spec.ForkChoice;
import org.ethereum.beacon.consensus.spec.GenesisFunction;
import org.ethereum.beacon.consensus.spec.HelperFunction;
import org.ethereum.beacon.consensus.spec.HonestValidator;
import org.ethereum.beacon.consensus.spec.SpecStateTransition;
import org.ethereum.beacon.consensus.util.CachingBeaconChainSpec;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.Millis;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.crypto.Hashes;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Beacon chain spec.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md">Beacon
 *     chain</a>
 */
public interface BeaconChainSpec
    extends HelperFunction,
        GenesisFunction,
        ForkChoice,
        EpochProcessing,
        BlockProcessing,
        SpecStateTransition,
        HonestValidator {

  SpecConstants DEFAULT_CONSTANTS = new SpecConstants() {};

  /**
   * Creates a BeaconChainSpec instance with given {@link SpecConstants}, {@link
   * Hashes#sha256(BytesValue)} as a hash function and {@link SSZObjectHasher} as an object hasher.
   *
   * @param constants a chain getConstants(). <code>Schedulers::currentTime</code> is passed
   * @return spec helpers instance.
   */
  static BeaconChainSpec createWithDefaultHasher(@Nonnull SpecConstants constants) {
    Objects.requireNonNull(constants);

    return new Builder()
        .withConstants(constants)
        .withDefaultHashFunction()
        .withDefaultHasher(constants)
        .build();
  }

  /**
   * Creates beacon chain spec with default preferences, disabled deposit root verification and
   * disabled genesis time computation.
   *
   * @param constants spec constants object.
   * @return spec object.
   */
  static BeaconChainSpec createWithoutDepositVerification(@Nonnull SpecConstants constants) {
    Objects.requireNonNull(constants);

    return new Builder()
        .withConstants(constants)
        .withDefaultHashFunction()
        .withDefaultHasher(constants)
        .withVerifyDepositProof(false)
        .withComputableGenesisTime(false)
        .build();
  }

  static BeaconChainSpec createWithDefaults() {
    return createWithDefaultHasher(DEFAULT_CONSTANTS);
  }

  default SlotNumber get_current_slot(BeaconState state, long systemTime) {
    Millis currentTime = Millis.of(systemTime);
    assertTrue(state.getGenesisTime().lessEqual(currentTime.getSeconds()));
    Time sinceGenesis = currentTime.getSeconds().minus(state.getGenesisTime());
    return SlotNumber.castFrom(sinceGenesis.dividedBy(getConstants().getSecondsPerSlot()))
        .plus(getConstants().getGenesisSlot());
  }

  default Time get_slot_start_time(BeaconState state, SlotNumber slot) {
    return state
        .getGenesisTime()
        .plus(
            getConstants().getSecondsPerSlot().times(slot.minus(getConstants().getGenesisSlot())));
  }

  default Time get_slot_middle_time(BeaconState state, SlotNumber slot) {
    return get_slot_start_time(state, slot).plus(getConstants().getSecondsPerSlot().dividedBy(2));
  }

  default boolean is_current_slot(BeaconState state, long systemTime) {
    return state.getSlot().equals(get_current_slot(state, systemTime));
  }

  class Builder {
    private SpecConstants constants;
    private Function<BytesValue, Hash32> hashFunction;
    private ObjectHasher<Hash32> hasher;
    private boolean cache = false;
    private boolean blsVerify = true;
    private boolean blsVerifyProofOfPossession = true;
    private boolean verifyDepositProof = true;
    private boolean computableGenesisTime = true;

    public static Builder createWithDefaultParams() {
      return new Builder()
          .withConstants(BeaconChainSpec.DEFAULT_CONSTANTS)
          .withDefaultHashFunction()
          .withDefaultHasher();
    }

    public Builder withConstants(SpecConstants constants) {
      this.constants = constants;
      return this;
    }

    public Builder withHashFunction(Function<BytesValue, Hash32> hashFunction) {
      this.hashFunction = hashFunction;
      return this;
    }

    public Builder withHasher(ObjectHasher<Hash32> hasher) {
      this.hasher = hasher;
      return this;
    }

    public Builder withBlsVerify(boolean blsVerify) {
      this.blsVerify = blsVerify;
      return this;
    }

    public Builder withCache(boolean cache) {
      this.cache = cache;
      return this;
    }

    public Builder withBlsVerifyProofOfPossession(boolean blsVerifyProofOfPossession) {
      this.blsVerifyProofOfPossession = blsVerifyProofOfPossession;
      return this;
    }

    public Builder withDefaultHashFunction() {
      return withHashFunction(Hashes::sha256);
    }

    public Builder withDefaultHasher(SpecConstants constants) {
      return withHasher(ObjectHasher.createSSZOverSHA256(constants));
    }

    public Builder withDefaultHasher() {
      assert constants != null;
      return withHasher(ObjectHasher.createSSZOverSHA256(constants));
    }

    public Builder enableCache() {
      return withCache(true);
    }

    public Builder withVerifyDepositProof(boolean verifyDepositProof) {
      this.verifyDepositProof = verifyDepositProof;
      return this;
    }

    public Builder withComputableGenesisTime(boolean computeGenesisTime) {
      this.computableGenesisTime = computeGenesisTime;
      return this;
    }

    public BeaconChainSpec build() {
      assert constants != null;
      assert hashFunction != null;
      assert hasher != null;

      return new CachingBeaconChainSpec(
          constants,
          hashFunction,
          hasher,
          blsVerify,
          blsVerifyProofOfPossession,
          verifyDepositProof,
          computableGenesisTime,
          cache);
    }
  }
}
