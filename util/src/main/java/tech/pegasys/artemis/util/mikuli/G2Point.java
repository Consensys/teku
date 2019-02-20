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

import java.security.SecureRandom;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.milagro.amcl.BLS381.ROM;

/**
 * G2 is the subgroup of elliptic curve similar to G1 and the points are identical except for where
 * they are elements of the extension field Fq12.
 */
final class G2Point implements Group<G2Point> {

  /**
   * Generate a random point on the curve
   *
   * @return a random point on the curve.
   */
  public static G2Point random() {
    SecureRandom rng = new SecureRandom();
    ECP2 point;
    byte[] xReBytes = new byte[48];
    byte[] xImBytes = new byte[48];

    // Repeatedly choose random X coords until one is on the curve. This generally takes only a
    // couple of attempts.
    do {
      rng.nextBytes(xReBytes);
      rng.nextBytes(xImBytes);
      point = new ECP2(new FP2(BIG.fromBytes(xReBytes), BIG.fromBytes(xImBytes)));
    } while (point.is_infinity());

    return new G2Point(point);
  }

  private final ECP2 point;

  // Bit 381 of the imaginary part of X. Equal to ((y_im * 2) / q == 1)
  private final boolean a1;
  // Bit 382 of the imaginary part of X. True only for the point at infinity.
  private final boolean b1;
  // Bit 383 of the imaginary part of X. Always true.
  private final boolean c1;

  private static final int fpPointSize = BIG.MODBYTES;

  G2Point(ECP2 point) {
    this.point = point;
    this.a1 = calculateA1flag(point.getY().getB());
    this.b1 = point.is_infinity();
    this.c1 = true;
  }

  G2Point(ECP2 point, boolean a1, boolean b1, boolean c1) {
    this.point = point;
    this.a1 = a1;
    this.b1 = b1;
    this.c1 = c1;
  }

  @Override
  public G2Point add(G2Point other) {
    ECP2 sum = new ECP2();
    sum.add(point);
    sum.add(other.point);
    sum.affine();
    return new G2Point(sum);
  }

  @Override
  public G2Point mul(Scalar scalar) {
    ECP2 newPoint = point.mul(scalar.value());
    return new G2Point(newPoint);
  }

  Bytes toBytes() {
    byte[] bytes = new byte[4 * fpPointSize];
    point.toBytes(bytes);
    return Bytes.wrap(bytes);
  }

  /**
   * Serialise the point into compressed form
   *
   * <p>In compresssed form we (a) pass only the X coordinate, and (b) include flags in the higher
   * order bits per the Eth2 BLS spec. We also take care about encoding the real and imaginary parts
   * in the correct order: [Im, Re]
   *
   * @return the serialised compressed form of the point
   */
  Bytes toBytesCompressed() {
    byte[] xReBytes = new byte[fpPointSize];
    byte[] xImBytes = new byte[fpPointSize];
    point.getX().getA().toBytes(xReBytes);
    point.getX().getB().toBytes(xImBytes);

    byte flags = (byte) ((a1 ? 1 << 5 : 0) | (b1 ? 1 << 6 : 0) | (c1 ? 1 << 7 : 0));
    byte mask = (byte) 31;
    xImBytes[0] &= mask;
    xImBytes[0] |= flags;

    return Bytes.concatenate(Bytes.wrap(xImBytes), Bytes.wrap(xReBytes));
  }

  static G2Point fromBytes(Bytes bytes) {
    return new G2Point(ECP2.fromBytes(bytes.toArrayUnsafe()));
  }

  /**
   * Deserialise the point from compressed form as per the Eth2 spec
   *
   * @param bytes the compressed serialised form of the point
   * @return the point
   */
  static G2Point fromBytesCompressed(Bytes bytes) {
    byte[] xIm = bytes.slice(0, fpPointSize).toArray();
    byte[] xRe = bytes.slice(fpPointSize, fpPointSize).toArray();

    boolean a = (xIm[0] & (byte) (1 << 5)) != 0;
    boolean b = (xIm[0] & (byte) (1 << 6)) != 0;
    boolean c = (xIm[0] & (byte) (1 << 7)) != 0;
    xIm[0] &= (byte) 31;

    G2Point point = new G2Point(new ECP2(new FP2(BIG.fromBytes(xRe), BIG.fromBytes(xIm))), a, b, c);

    // Did we get the right branch of the sqrt?
    if (a != calculateA1flag(point.ecp2Point().getY().getB())) {
      // We didn't: so choose the other branch of the sqrt.
      FP2 x = point.ecp2Point().getX();
      FP2 y1 = point.ecp2Point().getY();
      FP2 y2 = new FP2(y1);
      y2.neg();
      point = new G2Point(new ECP2(x, y2));
    }

    if (!isValid(point)) {
      throw new RuntimeException("Invalid point deserialised.");
    }

    return point;
  }

  /**
   * Check the validity of a G2 point according to the Eth2 spec.
   *
   * @return true if this is a valid point
   */
  static boolean isValid(G2Point point) {

    BIG xRe = point.ecp2Point().getX().getA();
    BIG xIm = point.ecp2Point().getX().getB();
    BIG yIm = point.ecp2Point().getY().getB();

    // Check xIm and xRe are both < q (the field modulus)
    // TODO: Check xIm and xRe are both < q (the field modulus)

    if (!point.c1) {
      return false;
    }

    // Deal with point at infinity
    if (point.b1) {
      return (!point.a1 && xRe.iszilch() && xIm.iszilch());
    }

    //
    if (point.a1 != calculateA1flag(yIm)) {
      return false;
    }

    // Check both X and Y are on the curve
    ECP2 newPoint = new ECP2(point.ecp2Point().getX(), point.ecp2Point().getY());
    return point.ecp2Point().equals(newPoint);
  }

  /**
   * Calculate (y_im * 2) // q (which corresponds to the a_flag1)
   *
   * <p>This is used to disambiguate Y, given X, as per the spec.
   *
   * @param yIm the imaginary part of the Y coordinate of the point
   * @return true if the a1 flag and yIm correspond
   */
  private static boolean calculateA1flag(BIG yIm) {
    BIG tmp = new BIG(yIm);
    tmp.add(yIm);
    tmp.div(new BIG(ROM.Modulus));
    return tmp.isunity();
  }

  ECP2 ecp2Point() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString() + " a1:" + a1 + " b1:" + b1 + " c1:" + c1;
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof G2Point)) {
      return false;
    }
    G2Point other = (G2Point) obj;
    return point.equals(other.point) && a1 == other.a1 && b1 == other.b1 && c1 == other.c1;
  }

  @Override
  public int hashCode() {
    // TODO: add a1, b1, c1
    final int prime = 31;
    int result = 1;
    long xa = point.getX().getA().norm();
    long ya = point.getY().getA().norm();
    long xb = point.getX().getB().norm();
    long yb = point.getY().getB().norm();
    result = prime * result + (int) (xa ^ (xa >>> 32));
    result = prime * result + (int) (ya ^ (ya >>> 32));
    result = prime * result + (int) (xb ^ (xb >>> 32));
    result = prime * result + (int) (yb ^ (yb >>> 32));
    return result;
  }
}
