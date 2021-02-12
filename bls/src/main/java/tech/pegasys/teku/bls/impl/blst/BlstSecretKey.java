/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.blst;

import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.P1;

public class BlstSecretKey implements SecretKey {
  static final BlstSecretKey ZERO_SK = BlstSecretKey.fromBytesRaw(Bytes32.ZERO);

  public static BlstSecretKey fromBytes(Bytes32 bytes) {
    if (bytes.isZero()) {
      return ZERO_SK;
    } else {
      return fromBytesRaw(bytes);
    }
  }

  private static BlstSecretKey fromBytesRaw(Bytes32 bytes) {
    tech.pegasys.teku.bls.impl.blst.swig.SecretKey secretKey =
        new tech.pegasys.teku.bls.impl.blst.swig.SecretKey();
    secretKey.from_bendian(bytes.toArrayUnsafe());
    return new BlstSecretKey(secretKey);
  }

  public static BlstSecretKey generateNew(Random random) {
    byte[] ikm = new byte[128];
    random.nextBytes(ikm);
    tech.pegasys.teku.bls.impl.blst.swig.SecretKey sk =
        new tech.pegasys.teku.bls.impl.blst.swig.SecretKey();
    sk.keygen(ikm);
    return new BlstSecretKey(sk);
  }

  private final tech.pegasys.teku.bls.impl.blst.swig.SecretKey secretKey;

  public BlstSecretKey(tech.pegasys.teku.bls.impl.blst.swig.SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public tech.pegasys.teku.bls.impl.blst.swig.SecretKey getKey() {
    return secretKey;
  }

  @Override
  public Bytes32 toBytes() {
    return Bytes32.wrap(secretKey.to_bendian());
  }

  @Override
  public Signature sign(Bytes message) {
    return BlstBLS12381.sign(this, message);
  }

  @Override
  public Signature sign(Bytes message, String dst) {
    return BlstBLS12381.sign(this, message, dst);
  }

  @Override
  public BlstPublicKey derivePublicKey() {
    P1 pk = null;
    try {
      pk = new P1(secretKey);
      return new BlstPublicKey(pk.to_affine());
    } finally {
      if (pk != null) {
        pk.delete();
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isZero() {
    return this == ZERO_SK;
  }

  @Override
  public int hashCode() {
    return toBytes().hashCode();
  }

  @Override
  public void destroy() {}

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SecretKey)) {
      return false;
    }
    return Objects.equals(toBytes(), ((SecretKey) o).toBytes());
  }
}
