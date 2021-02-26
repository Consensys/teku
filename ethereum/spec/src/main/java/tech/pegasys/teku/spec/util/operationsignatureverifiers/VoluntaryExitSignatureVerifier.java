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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.ValidatorsUtil;

public class VoluntaryExitSignatureVerifier {
  private final SpecConstants specConstants;
  private final BeaconStateUtil beaconStateUtil;
  private final ValidatorsUtil validatorsUtil;

  public VoluntaryExitSignatureVerifier(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorsUtil validatorsUtil) {
    this.specConstants = specConstants;
    this.beaconStateUtil = beaconStateUtil;
    this.validatorsUtil = validatorsUtil;
  }

  public boolean verifySignature(
      BeaconState state, SignedVoluntaryExit signedExit, BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        validatorsUtil.getValidatorPubKey(state, exit.getValidator_index());
    if (maybePublicKey.isEmpty()) {
      return false;
    }

    final Bytes32 domain =
        beaconStateUtil.getDomain(state, specConstants.getDomainVoluntaryExit(), exit.getEpoch());
    final Bytes signing_root = beaconStateUtil.computeSigningRoot(exit, domain);
    return signatureVerifier.verify(maybePublicKey.get(), signing_root, signedExit.getSignature());
  }
}
