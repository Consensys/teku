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

package tech.pegasys.teku.core.signatures;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_SELECTION_PROOF;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class SigningRootUtil {
  public static Bytes signingRootForRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    Bytes32 domain =
        get_domain(
            Constants.DOMAIN_RANDAO,
            epoch,
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(epoch.longValue(), domain);
  }

  public static Bytes signingRootForSignBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    final Bytes32 domain =
        get_domain(
            Constants.DOMAIN_BEACON_PROPOSER,
            compute_epoch_at_slot(block.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(block, domain);
  }

  public static Bytes signingRootForSignAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    final Bytes32 domain =
        get_domain(
            DOMAIN_BEACON_ATTESTER,
            attestationData.getTarget().getEpoch(),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(attestationData, domain);
  }

  public static Bytes signingRootForSignAggregationSlot(
      final UInt64 slot, final ForkInfo forkInfo) {
    final Bytes32 domain =
        get_domain(
            DOMAIN_SELECTION_PROOF,
            compute_epoch_at_slot(slot),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(slot.longValue(), domain);
  }

  public static Bytes signingRootForSignAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    final Bytes32 domain =
        get_domain(
            Constants.DOMAIN_AGGREGATE_AND_PROOF,
            compute_epoch_at_slot(aggregateAndProof.getAggregate().getData().getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(aggregateAndProof, domain);
  }

  public static Bytes signingRootForSignVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    final Bytes32 domain =
        get_domain(
            Constants.DOMAIN_VOLUNTARY_EXIT,
            voluntaryExit.getEpoch(),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return compute_signing_root(voluntaryExit, domain);
  }
}
