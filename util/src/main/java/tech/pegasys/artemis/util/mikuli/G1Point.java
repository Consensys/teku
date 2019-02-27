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

import com.google.common.annotations.VisibleForTesting;
import java.security.SecureRandom;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.FP;

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

  static G1Point fromBytes(Bytes bytes) {
    return new G1Point(ECP.fromBytes(bytes.toArrayUnsafe()));
  }

  static G1Point fromBytesCompressed(Bytes bytes) {
    checkArgument(
        bytes.size() == fpPointSize,
        "Expected %s bytes but received %s.",
        fpPointSize,
        bytes.size());
    byte[] xBytes = bytes.toArray();

    boolean a = (xBytes[0] & (byte) (1 << 5)) != 0;
    boolean b = (xBytes[0] & (byte) (1 << 6)) != 0;
    boolean c = (xBytes[0] & (byte) (1 << 7)) != 0;
    xBytes[0] &= (byte) 31;

    ECP point = new ECP(BIG.fromBytes(xBytes));

    // Did we get the right branch of the sqrt?
    if (!point.is_infinity() && a != calculateYFlag(point.getY())) {
      // We didn't: so choose the other branch of the sqrt.
      FP x = new FP(point.getX());
      FP yneg = new FP(point.getY());
      yneg.neg();
      point = new ECP(x.redc(), yneg.redc());
    }

    return new G1Point(point, a, b, c);
  }

  private final ECP point;

  // Bit 381 of the imaginary part of X. Equal to ((y_im * 2) / q == 1)
  private final boolean a;
  // Bit 382 of the imaginary part of X. True only for the point at infinity.
  private final boolean b;
  // Bit 383 of the imaginary part of X. Always true.
  private final boolean c;

  private static final int fpPointSize = BIG.MODBYTES;

  /** Default constructor creates the point at infinity (the zero point) */
  public G1Point() {
    this(new ECP(), false, true, true);
  }

  /**
   * Constructor for point that calculates the correct flags as per Eth2 Spec
   *
   * <p>Will throw an exception if an invalid point is specified. We don't want to crash since this
   * may simply be due to bad data that has been sent to us, so the exception needs to be caught and
   * handled higher up the stack.
   *
   * @param point the ec2p point
   * @throws IllegalArgumentException if the point is not on the curve
   */
  G1Point(ECP point) {
    this(point, !point.is_infinity() && calculateYFlag(point.getY()), point.is_infinity(), true);
  }

  /**
   * Constructor for point with flags as per Eth2 Spec
   *
   * <p>Will throw an exception if an invalid point is specified. We don't want to crash since this
   * may simply be due to bad data that has been sent to us, so the exception needs to be caught and
   * handled higher up the stack.
   *
   * @param point the ec2p point
   * @param a1 the Y coordinate branch flag
   * @param b1 the infinity flag
   * @param c1 always true
   * @throws IllegalArgumentException if the point is not on the curve or the flags are incorrect
   */
  private G1Point(ECP point, boolean a1, boolean b1, boolean c1) {
    checkArgument(isValid(point, a1, b1, c1), "Trying to create invalid point.");
    this.point = point;
    this.a = a1;
    this.b = b1;
    this.c = c1;
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

    byte flags = (byte) ((a ? 1 << 5 : 0) | (b ? 1 << 6 : 0) | (c ? 1 << 7 : 0));
    byte mask = (byte) 31;
    xBytes[0] &= mask;
    xBytes[0] |= flags;

    return Bytes.wrap(xBytes);
  }

  /**
   * Check the validity of a G2 point according to the Eth2 spec.
   *
   * @return true if this is a valid point
   */
  static boolean isValid(G1Point point) {
    return isValid(point.ecpPoint(), point.a, point.b, point.c);
  }

  /**
   * Check the validity of an ECP2 point and its flags according to the Eth2 spec.
   *
   * @return true if point is consistent with the flags
   */
  @VisibleForTesting
  static boolean isValid(ECP point, boolean a, boolean b, boolean c) {
    BIG x = point.getX();
    BIG y = point.getY();

    // Check xIm and xRe are both < q (the field modulus)
    // TODO: Check x < q (the field modulus)

    if (!c) {
      return false;
    }

    // Point at infinity
    if (b != point.is_infinity()) {
      return false;
    }
    if (b) {
      return (!a && x.iszilch());
    }

    // Check that we have the right branch for Y
    if (a != calculateYFlag(y)) {
      return false;
    }

    // Check that both X and Y are on the curve
    ECP newPoint = new ECP(point.getX(), point.getY());
    return point.equals(newPoint);
  }

  ECP ecpPoint() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString() + " a:" + a + " b:" + b + " c:" + 1;
  }

  @Override
  public int hashCode() {
    // TODO: add a, b, c
    final int prime = 31;
    int result = 1;
    long x = point.getX().norm();
    long y = point.getY().norm();
    result = prime * result + (int) (x ^ (x >>> 32));
    result = prime * result + (int) (y ^ (y >>> 32));
    return result;
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
    return point.equals(other.point) && a == other.a && b == other.b && c == other.c;
  }

  // Getters used only for testing

  boolean getA() {
    return a;
  }

  boolean getB() {
    return b;
  }

  boolean getC() {
    return c;
  }
}
