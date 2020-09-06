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

package tech.pegasys.teku.datastructures.util;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_domain;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.util.config.Constants.BLS_WITHDRAWAL_PREFIX;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_DEPOSIT;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.infrastructure.crypto.BouncyCastleMessageDigestFactory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositGenerator {

  private final boolean signDeposit;

  public DepositGenerator() {
    this(true);
  }

  public DepositGenerator(boolean signDeposit) {
    this.signDeposit = signDeposit;
  }

  public DepositData createDepositData(
      final BLSKeyPair validatorKeyPair,
      final UInt64 amountInGwei,
      final BLSPublicKey withdrawalPublicKey) {
    final Bytes32 withdrawalCredentials = createWithdrawalCredentials(withdrawalPublicKey);
    final DepositMessage depositMessage =
        new DepositMessage(validatorKeyPair.getPublicKey(), withdrawalCredentials, amountInGwei);
    final BLSSignature signature =
        signDeposit
            ? BLS.sign(
                validatorKeyPair.getSecretKey(),
                compute_signing_root(depositMessage, compute_domain(DOMAIN_DEPOSIT)))
            : BLSSignature.empty();
    return new DepositData(depositMessage, signature);
  }

  private Bytes32 createWithdrawalCredentials(final BLSPublicKey withdrawalPublicKey) {
    final Bytes publicKeyHash = sha256(withdrawalPublicKey.toBytesCompressed());
    final Bytes credentials = Bytes.wrap(BLS_WITHDRAWAL_PREFIX, publicKeyHash.slice(1));
    return Bytes32.wrap(credentials);
  }

  private Bytes sha256(final Bytes indexBytes) {
    final MessageDigest sha256Digest = getSha256Digest();
    indexBytes.update(sha256Digest);
    return Bytes.wrap(sha256Digest.digest());
  }

  private MessageDigest getSha256Digest() {
    try {
      return BouncyCastleMessageDigestFactory.create("sha256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
