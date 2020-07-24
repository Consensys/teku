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

  public static BlstSecretKey fromBytes(Bytes32 bytes) {
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

  public final scalar scalarVal;

  public BlstSecretKey(scalar scalarVal) {
    this.scalarVal = scalarVal;
  }

  @Override
  public Bytes32 toBytes() {
    byte[] res = new byte[32];
    blst.bendian_from_scalar(res, scalarVal);
    return Bytes32.wrap(res);
  }

  @Override
  public Signature sign(Bytes message) {
    return BlstBLS12381.sign(this, message);
  }

  @Override
  public void destroy() {
    // TODO
  }

  @Override
  public BlstPublicKey derivePublicKey() {
    p1 pk = new p1();
    blst.sk_to_pk_in_g1(pk, scalarVal);
    p1_affine pkAffine = new p1_affine();
    blst.p1_to_affine(pkAffine, pk);
    pk.delete();
    return new BlstPublicKey(pkAffine);
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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BlstSecretKey that = (BlstSecretKey) o;
    return Objects.equals(toBytes(), that.toBytes());
  }
}
