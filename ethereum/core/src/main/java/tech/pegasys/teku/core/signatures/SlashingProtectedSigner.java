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

import com.google.common.primitives.UnsignedLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * A wrapper for a {@link Signer} which adds slashing protection based on
 * https://hackmd.io/u8MTIe5IRmybzVz38-sGdQ?view.
 *
 * <p>A validator can only be slashed because of the blocks or attestations they sign, so all other
 * methods are delegated without any checks.
 *
 * <p>For blocks, the slot of the last signed block is recorded and signing is only allowed if the
 * slot is greater than the previous.
 *
 * <p>For attestations, the last source epoch and target epoch are recorded. An attestation may only
 * be signed if source >= previousSource AND target > previousTarget
 */
public class SlashingProtectedSigner implements Signer {

  private final BLSPublicKey validatorPublicKey;
  private final SlashingProtection slashingProtection;
  private final Signer delegate;

  public SlashingProtectedSigner(
      final BLSPublicKey validatorPublicKey,
      final SlashingProtection slashingProtection,
      final Signer delegate) {
    this.validatorPublicKey = validatorPublicKey;
    this.slashingProtection = slashingProtection;
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return slashingProtection
        .maySignBlock(validatorPublicKey, block.getSlot())
        .thenAccept(verifySigningAllowed(slashableBlockMessage(block)))
        .thenCompose(__ -> delegate.signBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return slashingProtection
        .maySignAttestation(
            validatorPublicKey,
            attestationData.getSource().getEpoch(),
            attestationData.getTarget().getEpoch())
        .thenAccept(verifySigningAllowed(slashableAttestationMessage(attestationData)))
        .thenCompose(__ -> delegate.signAttestationData(attestationData, forkInfo));
  }

  private Supplier<String> slashableBlockMessage(final BeaconBlock block) {
    return () ->
        "Refusing to sign block at slot "
            + block.getSlot()
            + " as it may violate a slashing condition";
  }

  private Supplier<String> slashableAttestationMessage(final AttestationData attestationData) {
    return () ->
        "Refusing to sign attestation at slot "
            + attestationData.getSlot()
            + " with source epoch "
            + attestationData.getSource().getEpoch()
            + " and target epoch "
            + attestationData.getTarget().getEpoch()
            + " because it may violate a slashing condition";
  }

  private Consumer<Boolean> verifySigningAllowed(final Supplier<String> messageSupplier) {
    return maySign -> {
      if (!maySign) {
        throw new SlashableConditionException(messageSupplier.get());
      }
    };
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(
      final UnsignedLong epoch, final ForkInfo forkInfo) {
    return delegate.createRandaoReveal(epoch, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(
      final UnsignedLong slot, final ForkInfo forkInfo) {
    return delegate.signAggregationSlot(slot, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return delegate.signAggregateAndProof(aggregateAndProof, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return delegate.signVoluntaryExit(voluntaryExit, forkInfo);
  }

  @Override
  public boolean isLocal() {
    return delegate.isLocal();
  }
}
