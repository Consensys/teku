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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes32;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

public class MockStartValidatorKeyPairFactory {
  private static final int KEY_LENGTH = 48;
  private static final BigInteger CURVE_ORDER =
      new BigInteger(
          "52435875175126190479447740508185965837690552500527637822603658699938581184513");

  public List<BLSKeyPair> generateKeyPairs(final int startIndex, final int endIndex) {
    return IntStream.range(startIndex, endIndex)
        .mapToObj(this::createKeyPairForValidator)
        .collect(Collectors.toList());
  }

  public List<BLSKeyPair> generateKeyPairs(final int count) {
    return generateKeyPairs(0, count);
  }

  private BLSKeyPair createKeyPairForValidator(final int validatorIndex) {
    final Bytes hash = sha256(int_to_bytes32(validatorIndex));
    final BigInteger privKey = hash.reverse().toUnsignedBigInteger().mod(CURVE_ORDER);
    final Bytes privKeyBytes = padLeft(Bytes.of(privKey.toByteArray()), KEY_LENGTH);

    return new BLSKeyPair(new KeyPair(SecretKey.fromBytes(privKeyBytes)));
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

  private Bytes padLeft(Bytes input, int targetLength) {
    return Bytes.concatenate(Bytes.wrap(new byte[targetLength - input.size()]), input);
  }
}
