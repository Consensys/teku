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

package org.ethereum.beacon.discovery.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.type.BytesValue;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;

public class CryptoUtil {
  public static Bytes sha256(final Bytes indexBytes) {
    final MessageDigest sha256Digest = getSha256Digest();
    indexBytes.update(sha256Digest);
    return Bytes.wrap(sha256Digest.digest());
  }

  public static Bytes sha256(final BytesValue indexBytes) {
    final MessageDigest sha256Digest = getSha256Digest();
    indexBytes.update(sha256Digest);
    return Bytes.wrap(sha256Digest.digest());
  }

  private static MessageDigest getSha256Digest() {
    try {
      return BouncyCastleMessageDigestFactory.create("sha256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
