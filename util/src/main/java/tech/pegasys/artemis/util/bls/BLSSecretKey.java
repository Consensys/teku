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

package tech.pegasys.artemis.util.bls;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.mikuli.SecretKey;

public class BLSSecretKey {

  public static BLSSecretKey fromBytes(Bytes bytes48) {
    return new BLSSecretKey(SecretKey.fromBytes(bytes48));
  }

  private SecretKey secretKey;

  BLSSecretKey(SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public SecretKey getSecretKey() {
    return secretKey;
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
