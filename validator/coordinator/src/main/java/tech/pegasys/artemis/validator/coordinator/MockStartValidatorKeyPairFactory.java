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

package tech.pegasys.artemis.validator.coordinator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

public class MockStartValidatorKeyPairFactory implements ValidatorKeyPairFactory {

  @Override
  public List<BLSKeyPair> generateKeyPairs(final int startIndex, final int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .mapToObj(
            index -> {
              final Bytes32 indexBytes = BeaconStateUtil.int_to_bytes32(index);
              final MessageDigest sha256Digest = getSha256Digest();
              indexBytes.update(sha256Digest);
              final Bytes hash = Bytes.wrap(sha256Digest.digest());
              final Bytes privKeyBytes =
                  Bytes.concatenate(Bytes.wrap(new byte[17]), hash.slice(0, hash.size() - 1));

              return new BLSKeyPair(new KeyPair(SecretKey.fromBytes(privKeyBytes)));
            })
        .collect(Collectors.toList());
  }

  private MessageDigest getSha256Digest() {
    try {
      return BouncyCastleMessageDigestFactory.create("sha256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
