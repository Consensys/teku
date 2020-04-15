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

package tech.pegasys.artemis.core;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_SELECTION_PROOF;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class Signer {
  private final MessageSignerService signerService;

  public Signer(final MessageSignerService signerService) {
    this.signerService = signerService;
  }

  public SafeFuture<BLSSignature> createRandaoReveal(
      final UnsignedLong epoch, final ForkInfo forkInfo) {
    Bytes domain =
        get_domain(
            Constants.DOMAIN_RANDAO,
            epoch,
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    Bytes signing_root = compute_signing_root(epoch.longValue(), domain);
    return signerService.signRandaoReveal(signing_root);
  }

  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    final Bytes domain =
        get_domain(
            Constants.DOMAIN_BEACON_PROPOSER,
            compute_epoch_at_slot(block.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    final Bytes signing_root = compute_signing_root(block, domain);
    return signerService.signBlock(signing_root);
  }

  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    final Bytes domain =
        get_domain(
            DOMAIN_BEACON_ATTESTER,
            attestationData.getTarget().getEpoch(),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    final Bytes signingRoot = compute_signing_root(attestationData, domain);
    return signerService.signAttestation(signingRoot);
  }

  public SafeFuture<BLSSignature> signAggregationSlot(
      final UnsignedLong slot, final ForkInfo forkInfo) {
    final Bytes domain =
        get_domain(
            DOMAIN_SELECTION_PROOF,
            compute_epoch_at_slot(slot),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    final Bytes signingRoot = compute_signing_root(slot.longValue(), domain);
    return signerService.signAggregationSlot(signingRoot);
  }

  public MessageSignerService getMessageSignerService() {
    return signerService;
  }
}
