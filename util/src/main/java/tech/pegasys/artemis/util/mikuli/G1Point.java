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

package tech.pegasys.artemis.util.mikuli;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.util.mikuli.Util.calculateYFlag;

import java.security.SecureRandom;
import java.util.Objects;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;

/**
 * G1 is a subgroup of an elliptic curve whose points are elements of the finite field Fp - simple
 * numbers mod some prime p. The curve is defined by: y^2 = x^3 + 4
 */
final class G1Point implements Group<G1Point> {

  /**
   * Generate a random point on the curve
   *
   * @return a random point on the curve.
   */
  public static G1Point random() {
    SecureRandom rng = new SecureRandom();
    ECP point;
    byte[] xBytes = new byte[48];

    // Repeatedly choose random X coords until one is on the curve. This generally takes only a
    // couple of attempts.
    do {
      rng.nextBytes(xBytes);
      point = new ECP(BIG.fromBytes(xBytes));
    } while (point.is_infinity());

    return new G1Point(point);
  }

  /**
   * Deserialise the point from compressed form as per the Eth2 spec
   *
   * @param bytes the compressed serialised form of the point
   * @return the point
   */
  static G1Point fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 49, "Expected 49 bytes, received %s.", bytes.size());
    return new G1Point(ECP.fromBytes(bytes.toArrayUnsafe()));
  }

  static G1Point fromBytesCompressed(Bytes bytes) {
    checkArgument(
        bytes.size() == fpPointSize,
        "Expected %s bytes but received %s.",
        fpPointSize,
        bytes.size());
    byte[] xBytes = bytes.toArray();

    boolean aIn = (xBytes[0] & (byte) (1 << 5)) != 0;
    boolean bIn = (xBytes[0] & (byte) (1 << 6)) != 0;
    boolean cIn = (xBytes[0] & (byte) (1 << 7)) != 0;
    xBytes[0] &= (byte) 31;

    if (!cIn) {
      throw new IllegalArgumentException("The serialised input does not have the C flag set.");
    }

    if (bIn) {
      if (!aIn && Bytes.wrap(xBytes).isZero()) {
        // This is a correctly formed serialisation of infinity
        return new G1Point();
      } else {
        // The input is malformed
        throw new IllegalArgumentException(
            "The serialised input has B flag set, but A flag is set, or X is non-zero.");
      }
    }

    // Per the spec, we must check that x < q (the curve modulus) for this serialisation to be valid
    // We raise an exception (that should be caught) if this check fails: somebody might feed us
    // faulty input.
    BIG xBig = BIG.fromBytes(xBytes);
    BIG modulus = new BIG(ROM.Modulus);
    if (BIG.comp(modulus, xBig) <= 0) {
      throw new IllegalArgumentException("X coordinate is too large.");
    }

    ECP point = new ECP(xBig);

    if (point.is_infinity()) {
      throw new IllegalArgumentException("X coordinate is not on the curve.");
    }

    // Did we get the right branch of the sqrt?
    if (!point.is_infinity() && aIn != calculateYFlag(point.getY())) {
      // We didn't: so choose the other branch of the sqrt.
      FP x = new FP(point.getX());
      FP yneg = new FP(point.getY());
      yneg.neg();
      point = new ECP(x.redc(), yneg.redc());
    }

    return new G1Point(point);
  }

  private final ECP point;

  private static final int fpPointSize = BIG.MODBYTES;

  /** Default constructor creates the point at infinity (the zero point) */
  G1Point() {
    this(new ECP());
  }

  /**
   * Constructor for point
   *
   * @param point the ecp point
   */
  G1Point(ECP point) {
    this.point = point;
  }

  @Override
  public G1Point add(G1Point other) {
    ECP sum = new ECP();
    sum.add(point);
    sum.add(other.point);
    sum.affine();
    return new G1Point(sum);
  }

  @Override
  public G1Point mul(Scalar scalar) {
    ECP newPoint = point.mul(scalar.value());
    return new G1Point(newPoint);
  }

  Bytes toBytes() {
    // Size of the byte array representing compressed ECP point for BLS12-381 is
    // 49 bytes in milagro
    // size of the point = 48 bytes
    // meta information (parity bit, curve type etc) = 1 byte
    byte[] bytes = new byte[fpPointSize + 1];
    point.toBytes(bytes, true);
    return Bytes.wrap(bytes);
  }

  /**
   * Serialise the point into compressed form
   *
   * <p>In compresssed form we (a) pass only the X coordinate, and (b) include flags in the higher
   * order bits per the Eth2 BLS spec.
   *
   * @return the serialised compressed form of the point
   */
  Bytes toBytesCompressed() {
    byte[] xBytes = new byte[fpPointSize];
    point.getX().toBytes(xBytes);

    // Serialisation flags as defined in the Eth2 specs
    boolean c = true;
    boolean b = point.is_infinity();
    boolean a = !b && calculateYFlag(point.getY());

    byte flags = (byte) ((a ? 1 << 5 : 0) | (b ? 1 << 6 : 0) | (c ? 1 << 7 : 0));
    byte mask = (byte) 31;
    xBytes[0] &= mask;
    xBytes[0] |= flags;

    return Bytes.wrap(xBytes);
  }

  ECP ecpPoint() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(point.getX().norm(), point.getY().norm());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof G1Point)) {
      return false;
    }
    G1Point other = (G1Point) obj;
    return point.equals(other.point);
  }
}
