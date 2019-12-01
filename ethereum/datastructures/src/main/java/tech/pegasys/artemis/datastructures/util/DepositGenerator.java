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

package tech.pegasys.artemis.datastructures.util;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_domain;
import static tech.pegasys.artemis.util.config.Constants.BLS_WITHDRAWAL_PREFIX;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_DEPOSIT;

import com.google.common.primitives.UnsignedLong;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;

public class DepositGenerator {

  public DepositData createDepositData(
      final BLSKeyPair validatorKeyPair,
      final BLSKeyPair withdrawalKeyPair,
      final UnsignedLong amountInGwei) {
    final Bytes32 withdrawalCredentials = createWithdrawalCredentials(withdrawalKeyPair);
    final DepositData depositData =
        new DepositData(validatorKeyPair.getPublicKey(), withdrawalCredentials, amountInGwei, null);

    depositData.setSignature(
        BLSSignature.sign(
            validatorKeyPair,
            depositData.signing_root("signature"),
            compute_domain(DOMAIN_DEPOSIT)));
    return depositData;
  }

  private Bytes32 createWithdrawalCredentials(final BLSKeyPair keyPair) {
    final Bytes publicKeyHash = sha256(keyPair.getPublicKey().toBytesCompressed());
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
