/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.crypto;

import java.security.MessageDigest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class Hash {
  public static Bytes32 sha256(final byte[] input) {
    return Bytes32.wrap(MessageDigestFactory.createSha256().digest(input));
  }

  public static Bytes32 sha256(final Bytes input) {
    final MessageDigest digest = MessageDigestFactory.createSha256();
    input.update(digest);
    return Bytes32.wrap(digest.digest());
  }

  // Note: Doesn't use varargs to avoid creating a Bytes[] instance.
  public static Bytes32 sha256(final Bytes a, final Bytes b) {
    final MessageDigest digest = MessageDigestFactory.createSha256();
    a.update(digest);
    b.update(digest);
    return Bytes32.wrap(digest.digest());
  }

  // Note: Doesn't use varargs to avoid creating a Bytes[] instance.
  public static Bytes32 sha256(final Bytes a, final Bytes b, final Bytes c) {
    final MessageDigest digest = MessageDigestFactory.createSha256();
    a.update(digest);
    b.update(digest);
    c.update(digest);
    return Bytes32.wrap(digest.digest());
  }
}
