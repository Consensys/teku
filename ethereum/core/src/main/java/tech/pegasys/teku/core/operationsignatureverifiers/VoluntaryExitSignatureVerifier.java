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

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_VOLUNTARY_EXIT;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;

public class VoluntaryExitSignatureVerifier {

  public boolean verifySignature(
      BeaconState state, SignedVoluntaryExit signedExit, BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();

    BLSPublicKey publicKey =
        BeaconStateCache.getTransitionCaches(state)
            .getValidatorsPubKeys()
            .get(
                exit.getValidator_index(),
                idx -> state.getValidators().get(toIntExact(idx.longValue())).getPubkey());

    final Bytes32 domain = get_domain(state, DOMAIN_VOLUNTARY_EXIT, exit.getEpoch());
    final Bytes signing_root = compute_signing_root(exit, domain);
    return signatureVerifier.verify(publicKey, signing_root, signedExit.getSignature());
  }
}
