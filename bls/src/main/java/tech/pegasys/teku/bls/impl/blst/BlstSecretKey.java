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
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p1;
import tech.pegasys.teku.bls.impl.blst.swig.p1_affine;
import tech.pegasys.teku.bls.impl.blst.swig.scalar;

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
    scalar scalarVal = new scalar();
    blst.scalar_from_bendian(scalarVal, bytes.toArrayUnsafe());
    return new BlstSecretKey(scalarVal);
  }

  public static BlstSecretKey generateNew(Random random) {
    byte[] ikm = new byte[128];
    random.nextBytes(ikm);
    scalar sk = new scalar();
    blst.keygen(sk, ikm, null);
    return new BlstSecretKey(sk);
  }

  private final scalar scalarVal;
  private boolean destroyed = false;

  public BlstSecretKey(scalar scalarVal) {
    this.scalarVal = scalarVal;
  }

  @Override
  public Bytes32 toBytes() {
    byte[] res = new byte[32];
    blst.bendian_from_scalar(res, getScalarVal());
    return Bytes32.wrap(res);
  }

  @Override
  public Signature sign(Bytes message) {
    return BlstBLS12381.sign(this, message);
  }

  @Override
  public Signature sign(Bytes message, Bytes dst) {
    return BlstBLS12381.sign(this, message, dst);
  }

  @Override
  public void destroy() {
    blst.scalar_from_bendian(getScalarVal(), Bytes32.ZERO.toArrayUnsafe());
    destroyed = true;
  }

  @Override
  public BlstPublicKey derivePublicKey() {
    if (isZero()) {
      return BlstPublicKey.INFINITY;
    }
    p1 pk = new p1();
    try {
      blst.sk_to_pk_in_g1(pk, getScalarVal());
      p1_affine pkAffine = new p1_affine();
      blst.p1_to_affine(pkAffine, pk);

      return new BlstPublicKey(pkAffine);
    } finally {
      pk.delete();
    }
  }

  scalar getScalarVal() {
    if (destroyed) throw new IllegalStateException("Private key was destroyed");
    return scalarVal;
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
