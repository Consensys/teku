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

package tech.pegasys.teku.core.signatures;

import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregateAndProof;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregationSlot;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAttestationData;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignBlock;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignVoluntaryExit;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSigningData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public class LocalSigner implements Signer {

  private final Spec spec;
  private final BLSKeyPair keypair;
  private final AsyncRunner asyncRunner;

  public LocalSigner(final Spec spec, final BLSKeyPair keypair, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.keypair = keypair;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return sign(SigningRootUtil.signingRootForRandaoReveal(epoch, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return sign(signingRootForSignBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return sign(signingRootForSignAttestationData(attestationData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return sign(signingRootForSignAggregationSlot(slot, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return sign(signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return sign(signingRootForSignVoluntaryExit(voluntaryExit, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSignature(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return SafeFuture.of(
            () ->
                spec.getSyncCommitteeUtil(slot)
                    .orElseThrow()
                    .getSyncCommitteeSignatureSigningRoot(
                        forkInfo, spec.computeEpochAtSlot(slot), beaconBlockRoot))
        .thenCompose(this::sign);
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncCommitteeSigningData signingData, final ForkInfo forkInfo) {
    return SafeFuture.of(
            () ->
                spec.getSyncCommitteeUtil(signingData.getSlot())
                    .orElseThrow()
                    .getSyncCommitteeSigningDataSigningRoot(signingData, forkInfo))
        .thenCompose(this::sign);
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return SafeFuture.of(
            () ->
                spec.getSyncCommitteeUtil(contributionAndProof.getContribution().getSlot())
                    .orElseThrow()
                    .getContributionAndProofSigningRoot(contributionAndProof, forkInfo))
        .thenCompose(this::sign);
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  private SafeFuture<BLSSignature> sign(final Bytes signing_root) {
    return asyncRunner.runAsync(
        () -> SafeFuture.completedFuture(BLS.sign(keypair.getSecretKey(), signing_root)));
  }
}
