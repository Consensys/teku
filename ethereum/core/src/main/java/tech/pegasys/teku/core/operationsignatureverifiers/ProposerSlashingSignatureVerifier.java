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

package tech.pegasys.teku.core.operationsignatureverifiers;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_PROPOSER;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;

public class ProposerSlashingSignatureVerifier {

  private static final Logger LOG = LogManager.getLogger();

  public boolean verifySignature(
      BeaconState state,
      ProposerSlashing proposerSlashing,
      BLSSignatureVerifier signatureVerifier) {

    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        ValidatorsUtil.getValidatorPubKey(state, header1.getProposerIndex());
    if (maybePublicKey.isEmpty()) {
      return false;
    }
    BLSPublicKey publicKey = maybePublicKey.get();

    if (!signatureVerifier.verify(
        publicKey,
        compute_signing_root(
            header1,
            get_domain(state, DOMAIN_BEACON_PROPOSER, compute_epoch_at_slot(header1.getSlot()))),
        proposerSlashing.getHeader_1().getSignature())) {
      LOG.trace("Header1 signature is invalid {}", header1);
      return false;
    }

    if (!signatureVerifier.verify(
        publicKey,
        compute_signing_root(
            header2,
            get_domain(state, DOMAIN_BEACON_PROPOSER, compute_epoch_at_slot(header2.getSlot()))),
        proposerSlashing.getHeader_2().getSignature())) {
      LOG.trace("Header2 signature is invalid {}", header1);
      return false;
    }
    return true;
  }
}
