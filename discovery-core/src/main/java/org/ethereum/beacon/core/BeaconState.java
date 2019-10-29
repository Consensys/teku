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

package org.ethereum.beacon.core;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.BeaconStateImpl;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.incremental.ObservableComposite;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.Bitvector;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.ReadVector;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Beacon chain state.
 *
 * @see BeaconBlock
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beacon-state">BeaconState
 *     in the spec</a>
 */
public interface BeaconState extends ObservableComposite {

  static BeaconState getEmpty() {
    return getEmpty(new SpecConstants() {});
  }

  static BeaconState getEmpty(SpecConstants specConst) {
    BeaconStateImpl ret = new BeaconStateImpl(specConst);
    ret.getRandaoMixes()
        .addAll(
            Collections.nCopies(specConst.getEpochsPerHistoricalVector().intValue(), Hash32.ZERO));
    ret.getBlockRoots()
        .addAll(Collections.nCopies(specConst.getSlotsPerHistoricalRoot().intValue(), Hash32.ZERO));
    ret.getStateRoots()
        .addAll(Collections.nCopies(specConst.getSlotsPerHistoricalRoot().intValue(), Hash32.ZERO));
    ret.getActiveIndexRoots()
        .addAll(
            Collections.nCopies(specConst.getEpochsPerHistoricalVector().intValue(), Hash32.ZERO));
    ret.getSlashings()
        .addAll(Collections.nCopies(specConst.getEpochsPerSlashingsVector().intValue(), Gwei.ZERO));
    ret.getPreviousCrosslinks()
        .addAll(Collections.nCopies(specConst.getShardCount().intValue(), Crosslink.EMPTY));
    ret.getCurrentCrosslinks()
        .addAll(Collections.nCopies(specConst.getShardCount().intValue(), Crosslink.EMPTY));
    ret.getCompactCommitteesRoots()
        .addAll(
            Collections.nCopies(specConst.getEpochsPerHistoricalVector().intValue(), Hash32.ZERO));
    ret.setJustificationBits(Bitvector.of(specConst.getJustificationBitsLength(), 0));
    return ret;
  }

  /* ******* Versioning ********* */

  /** Timestamp of the genesis. */
  @SSZ(order = 0)
  Time getGenesisTime();

  /** Slot number that this state was calculated in. */
  @SSZ(order = 1)
  SlotNumber getSlot();

  /** Fork data corresponding to the {@link #getSlot()}. */
  @SSZ(order = 2)
  Fork getFork();

  /* ******* History ********* */

  @SSZ(order = 3)
  BeaconBlockHeader getLatestBlockHeader();

  @SSZ(order = 4, vectorLengthVar = "spec.SLOTS_PER_HISTORICAL_ROOT")
  ReadVector<SlotNumber, Hash32> getBlockRoots();

  @SSZ(order = 5, vectorLengthVar = "spec.SLOTS_PER_HISTORICAL_ROOT")
  ReadVector<SlotNumber, Hash32> getStateRoots();

  @SSZ(order = 6, maxSizeVar = "spec.HISTORICAL_ROOTS_LIMIT")
  ReadList<Integer, Hash32> getHistoricalRoots();

  /* ******* Eth1 ********* */

  /** Latest processed eth1 data. */
  @SSZ(order = 7)
  Eth1Data getEth1Data();

  /** Eth1 data that voting is still in progress for. */
  @SSZ(order = 8, maxSizeVar = "spec.SLOTS_PER_ETH1_VOTING_PERIOD")
  ReadList<Integer, Eth1Data> getEth1DataVotes();

  /** The most recent Eth1 deposit index */
  @SSZ(order = 9)
  UInt64 getEth1DepositIndex();

  /* ******* Registry ********* */

  /** Validator registry records. */
  @SSZ(order = 10, maxSizeVar = "spec.VALIDATOR_REGISTRY_LIMIT")
  ReadList<ValidatorIndex, ValidatorRecord> getValidators();

  /** Validator balances. */
  @SSZ(order = 11, maxSizeVar = "spec.VALIDATOR_REGISTRY_LIMIT")
  ReadList<ValidatorIndex, Gwei> getBalances();

  /* ******* Shuffling ********* */

  /** The most recent randao mixes. */
  @SSZ(order = 12)
  ShardNumber getStartShard();

  @SSZ(order = 13, vectorLengthVar = "spec.EPOCHS_PER_HISTORICAL_VECTOR")
  ReadVector<EpochNumber, Hash32> getRandaoMixes();

  /** Active index digests for light clients. */
  @SSZ(order = 14, vectorLengthVar = "spec.EPOCHS_PER_HISTORICAL_VECTOR")
  ReadVector<EpochNumber, Hash32> getActiveIndexRoots();

  /** Committee digests for light clients. */
  @SSZ(order = 15, vectorLengthVar = "spec.EPOCHS_PER_HISTORICAL_VECTOR")
  ReadVector<EpochNumber, Hash32> getCompactCommitteesRoots();

  /* ******** Slashings ********* */

  /** Per-epoch sums of slashed effective balances. */
  @SSZ(order = 16, vectorLengthVar = "spec.EPOCHS_PER_SLASHINGS_VECTOR")
  ReadVector<EpochNumber, Gwei> getSlashings();

  /* ******** Attestations ********* */

  @SSZ(order = 17, maxSizeVar = "spec.MAX_EPOCH_ATTESTATIONS")
  ReadList<Integer, PendingAttestation> getPreviousEpochAttestations();

  @SSZ(order = 18, maxSizeVar = "spec.MAX_EPOCH_ATTESTATIONS")
  ReadList<Integer, PendingAttestation> getCurrentEpochAttestations();

  /* ******** Crosslinks ********* */

  /** Previous epoch snapshot. */
  @SSZ(order = 19, vectorLengthVar = "spec.SHARD_COUNT")
  ReadVector<ShardNumber, Crosslink> getPreviousCrosslinks();

  @SSZ(order = 20, vectorLengthVar = "spec.SHARD_COUNT")
  ReadVector<ShardNumber, Crosslink> getCurrentCrosslinks();

  /* ******** Finality ********* */

  /** Bit set for every recent justified epoch. */
  @SSZ(order = 21, vectorLengthVar = "spec.JUSTIFICATION_BITS_LENGTH")
  Bitvector getJustificationBits();

  /** Previous epoch snapshot. */
  @SSZ(order = 22)
  Checkpoint getPreviousJustifiedCheckpoint();

  @SSZ(order = 23)
  Checkpoint getCurrentJustifiedCheckpoint();

  @SSZ(order = 24)
  Checkpoint getFinalizedCheckpoint();

  /**
   * Returns mutable copy of this state. Any changes made to returned copy shouldn't affect this
   * instance
   */
  MutableBeaconState createMutableCopy();

  default boolean equalsHelper(BeaconState other) {
    return getSlot().equals(other.getSlot())
        && getGenesisTime().equals(other.getGenesisTime())
        && getFork().equals(other.getFork())
        && getValidators().equals(other.getValidators())
        && getBalances().equals(other.getBalances())
        && getRandaoMixes().equals(other.getRandaoMixes())
        && getStartShard().equals(other.getStartShard())
        && getPreviousEpochAttestations().equals(other.getPreviousEpochAttestations())
        && getCurrentEpochAttestations().equals(other.getCurrentEpochAttestations())
        && getPreviousJustifiedCheckpoint().equals(other.getPreviousJustifiedCheckpoint())
        && getCurrentJustifiedCheckpoint().equals(other.getCurrentJustifiedCheckpoint())
        && getFinalizedCheckpoint().equals(other.getFinalizedCheckpoint())
        && getPreviousCrosslinks().equals(other.getPreviousCrosslinks())
        && getCurrentCrosslinks().equals(other.getCurrentCrosslinks())
        && getBlockRoots().equals(other.getBlockRoots())
        && getStateRoots().equals(other.getStateRoots())
        && getActiveIndexRoots().equals(other.getActiveIndexRoots())
        && getSlashings().equals(other.getSlashings())
        && getLatestBlockHeader().equals(other.getLatestBlockHeader())
        && getHistoricalRoots().equals(other.getHistoricalRoots())
        && getEth1Data().equals(other.getEth1Data())
        && getEth1DataVotes().equals(other.getEth1DataVotes())
        && getEth1DepositIndex().equals(other.getEth1DepositIndex())
        && getCompactCommitteesRoots().equals(other.getCompactCommitteesRoots())
        && getJustificationBits().equals(other.getJustificationBits());
  }

  default String toStringShort(@Nullable SpecConstants spec) {
    String ret =
        "BeaconState["
            + "@ "
            + getSlot().toString(spec, getGenesisTime())
            + ", "
            + getFork().toString(spec)
            + ", validators: "
            + getValidators().size()
            + ", just/final epoch: "
            + getCurrentJustifiedCheckpoint().getEpoch().toString(spec)
            + "/"
            + getFinalizedCheckpoint().getEpoch().toString(spec);
    if (spec != null) {
      ret += ", latestBlocks=[...";
      for (SlotNumber slot : getSlot().minusSat(3).iterateTo(getSlot())) {
        Hash32 blockRoot = getBlockRoots().get(slot.modulo(spec.getSlotsPerHistoricalRoot()));
        ret += ", " + blockRoot.toStringShort();
      }
      ret += "]";

      List<PendingAttestation> attestations = getCurrentEpochAttestations().listCopy();
      attestations.addAll(getPreviousEpochAttestations().listCopy());

      ret +=
          ", attest:["
              + attestations.stream()
                  .map(ar -> ar.toStringShort())
                  .collect(Collectors.joining(", "))
              + "]";
    }
    ret += "]";

    return ret;
  }
}
