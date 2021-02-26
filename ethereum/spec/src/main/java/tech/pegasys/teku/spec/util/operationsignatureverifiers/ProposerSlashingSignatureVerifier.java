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

package tech.pegasys.teku.spec.util.operationsignatureverifiers;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.ValidatorsUtil;

public class ProposerSlashingSignatureVerifier {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConstants specConstants;
  private final BeaconStateUtil beaconStateUtil;
  private final ValidatorsUtil validatorsUtil;

  public ProposerSlashingSignatureVerifier(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorsUtil validatorsUtil) {
    this.specConstants = specConstants;
    this.beaconStateUtil = beaconStateUtil;
    this.validatorsUtil = validatorsUtil;
  }

  public boolean verifySignature(
      BeaconState state,
      ProposerSlashing proposerSlashing,
      BLSSignatureVerifier signatureVerifier) {

    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        validatorsUtil.getValidatorPubKey(state, header1.getProposerIndex());
    if (maybePublicKey.isEmpty()) {
      return false;
    }
    BLSPublicKey publicKey = maybePublicKey.get();

    if (!signatureVerifier.verify(
        publicKey,
        beaconStateUtil.computeSigningRoot(
            header1,
            beaconStateUtil.getDomain(
                state,
                specConstants.getDomainBeaconProposer(),
                beaconStateUtil.computeEpochAtSlot(header1.getSlot()))),
        proposerSlashing.getHeader_1().getSignature())) {
      LOG.trace("Header1 signature is invalid {}", header1);
      return false;
    }

    if (!signatureVerifier.verify(
        publicKey,
        beaconStateUtil.computeSigningRoot(
            header2,
            beaconStateUtil.getDomain(
                state,
                specConstants.getDomainBeaconProposer(),
                beaconStateUtil.computeEpochAtSlot(header2.getSlot()))),
        proposerSlashing.getHeader_2().getSignature())) {
      LOG.trace("Header2 signature is invalid {}", header1);
      return false;
    }
    return true;
  }
}
