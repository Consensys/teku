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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public interface Signer {

  SafeFuture<BLSSignature> createRandaoReveal(UInt64 epoch, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signBlock(BeaconBlock block, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAttestationData(AttestationData attestationData, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAggregationSlot(UInt64 slot, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAggregateAndProof(
      AggregateAndProof aggregateAndProof, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signVoluntaryExit(VoluntaryExit voluntaryExit, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signSyncCommitteeSignature(
      UInt64 slot, Bytes32 beaconBlockRoot, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      SyncAggregatorSelectionData selectionData, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signContributionAndProof(
      ContributionAndProof contributionAndProof, ForkInfo forkInfo);

  boolean isLocal();
}
