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

package tech.pegasys.teku.spec.logic.common.operations;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class OperationSignatureVerifier {
  private static final Logger LOG = LogManager.getLogger();

  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public OperationSignatureVerifier(
      final MiscHelpers miscHelpers, final BeaconStateAccessors beaconStateAccessors) {
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  public boolean verifyProposerSlashingSignature(
      BeaconState state,
      ProposerSlashing proposerSlashing,
      BLSSignatureVerifier signatureVerifier) {

    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        beaconStateAccessors.getValidatorPubKey(state, header1.getProposerIndex());
    if (maybePublicKey.isEmpty()) {
      return false;
    }
    BLSPublicKey publicKey = maybePublicKey.get();

    if (!signatureVerifier.verify(
        publicKey,
        miscHelpers.computeSigningRoot(
            header1,
            beaconStateAccessors.getDomain(
                state, Domain.BEACON_PROPOSER, miscHelpers.computeEpochAtSlot(header1.getSlot()))),
        proposerSlashing.getHeader_1().getSignature())) {
      LOG.trace("Header1 signature is invalid {}", header1);
      return false;
    }

    if (!signatureVerifier.verify(
        publicKey,
        miscHelpers.computeSigningRoot(
            header2,
            beaconStateAccessors.getDomain(
                state, Domain.BEACON_PROPOSER, miscHelpers.computeEpochAtSlot(header2.getSlot()))),
        proposerSlashing.getHeader_2().getSignature())) {
      LOG.trace("Header2 signature is invalid {}", header1);
      return false;
    }
    return true;
  }

  public boolean verifyVoluntaryExitSignature(
      BeaconState state, SignedVoluntaryExit signedExit, BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        beaconStateAccessors.getValidatorPubKey(state, exit.getValidator_index());
    if (maybePublicKey.isEmpty()) {
      return false;
    }

    final Bytes32 domain =
        beaconStateAccessors.getDomain(state, Domain.VOLUNTARY_EXIT, exit.getEpoch());
    final Bytes signing_root = miscHelpers.computeSigningRoot(exit, domain);
    return signatureVerifier.verify(maybePublicKey.get(), signing_root, signedExit.getSignature());
  }
}
