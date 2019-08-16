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

package tech.pegasys.artemis.datastructures.operations;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.artemis.datastructures.Constants.BLS_WITHDRAWAL_PREFIX;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bls_domain;

import com.google.common.primitives.UnsignedLong;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;

// TODO: This is very much in the wrong package and module.
public class MockStartDepositGenerator {

  public List<DepositData> createDeposits(final List<BLSKeyPair> validatorKeys) {
    return validatorKeys.stream().map(this::createDepositData).collect(toList());
  }

  private DepositData createDepositData(final BLSKeyPair keyPair) {
    final DepositData data =
        new DepositData(
            keyPair.getPublicKey(),
            createWithdrawalCredentials(keyPair),
            UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE),
            null);
    data.setSignature(
        BLSSignature.sign(keyPair, data.signing_root("signature"), bls_domain(DOMAIN_DEPOSIT)));
    return data;
  }

  private Bytes32 createWithdrawalCredentials(final BLSKeyPair keyPair) {
    final byte[] credentials = keyPair.getPublicKey().toBytes().toArray();
    credentials[0] = (byte) BLS_WITHDRAWAL_PREFIX;
    return Bytes32.wrap(sha256(Bytes.wrap(credentials)));
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
