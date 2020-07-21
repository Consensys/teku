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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.impl.SecretKey;

public final class BLSSecretKey {

  public static BLSSecretKey fromBytes(Bytes32 bytes) {
    return new BLSSecretKey(BLS.getBlsImpl().secretKeyFromBytes(bytes));
  }

  private SecretKey secretKey;

  /**
   * Construct from a Mikuli SecretKey object.
   *
   * @param secretKey A Mikuli SecretKey
   */
  public BLSSecretKey(SecretKey secretKey) {
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

  /** Overwrites the key with zeros so that it is no longer in memory */
  public void destroy() {
    secretKey.destroy();
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

  @Override
  public String toString() {
    return "BLSSecretKey{" + toBytes() + '}';
  }
}
