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

package tech.pegasys.teku.bls.impl.mikuli;

import static org.apache.milagro.amcl.BLS381.BIG.MODBYTES;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve.hashToG2;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve;

/** This class represents a BLS12-381 private key. */
public class MikuliSecretKey implements SecretKey {

  /**
   * Create a private key from bytes
   *
   * @param bytes the bytes of the private key
   * @return a new SecretKey object
   */
  public static MikuliSecretKey fromBytes(Bytes32 bytes) {
    return new MikuliSecretKey(new Scalar(BIG.fromBytes(Bytes48.leftPad(bytes).toArrayUnsafe())));
  }

  public static MikuliSecretKey fromSecretKey(SecretKey genericSecretKey) {
    if (genericSecretKey instanceof MikuliSecretKey) {
      return (MikuliSecretKey) genericSecretKey;
    } else {
      return fromBytes(genericSecretKey.toBytes());
    }
  }

  private final Scalar scalarValue;

  public MikuliSecretKey(Scalar value) {
    this.scalarValue = value;
  }

  @Override
  public Bytes32 toBytes() {
    byte[] bytea = new byte[MODBYTES];
    scalarValue.value().toBytes(bytea);
    return Bytes32.wrap(bytea, 16);
  }

  @Override
  public MikuliPublicKey derivePublicKey() {
    return new MikuliPublicKey(Util.g1Generator.mul(getScalarValue()));
  }

  @Override
  public MikuliSignature sign(Bytes message) {
    return sign(message, HashToCurve.ETH2_DST);
  }

  @Override
  public MikuliSignature sign(Bytes message, Bytes dst) {
    if (scalarValue.isZero()) {
      throw new IllegalArgumentException("Signing with zero private key is prohibited");
    } else {
      G2Point hashInGroup2 = new G2Point(hashToG2(message, dst));
      G2Point signaturePoint = hashInGroup2.mul(scalarValue);
      return new MikuliSignature(signaturePoint);
    }
  }

  public Scalar getScalarValue() {
    return scalarValue;
  }

  /** Overwrites the key with zeros so that it is no longer in memory */
  @Override
  public void destroy() {
    scalarValue.destroy();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SecretKey)) return false;
    MikuliSecretKey secretKey = MikuliSecretKey.fromSecretKey((SecretKey) o);
    return Objects.equals(scalarValue, secretKey.scalarValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scalarValue);
  }
}
