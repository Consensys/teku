/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.util;

import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.BLS_WITHDRAWAL_PREFIX;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;

public class DepositGenerator {

  private final Spec spec;
  private final boolean signDeposit;

  public DepositGenerator(final Spec spec) {
    this(spec, true);
  }

  public DepositGenerator(final Spec spec, final boolean signDeposit) {
    this.spec = spec;
    this.signDeposit = signDeposit;
  }

  public DepositData createDepositData(
      final BLSKeyPair validatorKeyPair,
      final UInt64 amountInGwei,
      final BLSPublicKey withdrawalPublicKey) {
    final Bytes32 withdrawalCredentials = createWithdrawalCredentials(withdrawalPublicKey);
    final DepositMessage depositMessage =
        new DepositMessage(validatorKeyPair.getPublicKey(), withdrawalCredentials, amountInGwei);
    final SpecVersion specVersion = spec.getGenesisSpec();
    final Bytes32 depositDomain = specVersion.miscHelpers().computeDomain(Domain.DEPOSIT);
    final BLSSignature signature =
        signDeposit
            ? BLS.sign(
                validatorKeyPair.getSecretKey(),
                specVersion.miscHelpers().computeSigningRoot(depositMessage, depositDomain))
            : BLSSignature.empty();
    return new DepositData(depositMessage, signature);
  }

  private Bytes32 createWithdrawalCredentials(final BLSPublicKey withdrawalPublicKey) {
    final Bytes publicKeyHash = Hash.sha256(withdrawalPublicKey.toBytesCompressed());
    final Bytes credentials = Bytes.wrap(BLS_WITHDRAWAL_PREFIX, publicKeyHash.slice(1));
    return Bytes32.wrap(credentials);
  }
}
