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

package tech.pegasys.teku.bls;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.mikuli.SecretKey;

public final class BLSSecretKey {

  public static BLSSecretKey fromBytes(Bytes bytes) {
    checkArgument(
        bytes.size() == 32 || bytes.size() == 48,
        "Expected 32 or 48 bytes but received %s.",
        bytes.size());
    final Bytes keyBytes = bytes.size() == 32 ? Bytes48.leftPad(bytes) : bytes;
    return new BLSSecretKey(SecretKey.fromBytes(keyBytes));
  }

  private SecretKey secretKey;

  /**
   * Construct from a Mikuli SecretKey object.
   *
   * @param secretKey A Mikuli SecretKey
   */
  BLSSecretKey(SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public SecretKey getSecretKey() {
    return secretKey;
  }

  public Bytes toBytes() {
    final Bytes bytes = secretKey.toBytes();
    if (bytes.size() == 48) {
      final int paddingLength = 48 - 32;
      if (bytes.slice(0, paddingLength).isZero()) {
        return bytes.slice(paddingLength, 32);
      }
    }
    return bytes;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BLSSecretKey that = (BLSSecretKey) o;
    return secretKey.equals(that.secretKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(secretKey);
  }
}
