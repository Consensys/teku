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
import java.security.Security;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.crypto.Hash;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

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

  /**
   * Hashes to the G2 curve as described in the Eth2 spec
   *
   * @param message the message to be hashed. ***This will be changing to the message digest***
   * @param domain the signature domain as defined in the Eth2 spec
   * @return a point from the G2 group representing the message hash
   */
  // TODO: latest spec says that we pass Bytes32 messageHash in. But the test cases are older and
  // use the message itself
  // public static G2Point hashToG2(Bytes32 messageHash, long domain) {
  static G2Point hashToG2(Bytes message, long domain) {
    Security.addProvider(new BouncyCastleProvider());
    Bytes domainBytes = Bytes.ofUnsignedLong(domain);
    Bytes padding = Bytes.wrap(new byte[16]);

    byte[] xReBytes =
        Bytes.concatenate(
                padding,
                Hash.keccak256(
                    // TODO:
                    // Bytes.concatenate(messageHash, domainBytes, Bytes.fromHexString("0x01"))))
                    Bytes.concatenate(message, domainBytes, Bytes.fromHexString("0x01"))))
            .toArray();
    byte[] xImBytes =
        Bytes.concatenate(
                padding,
                Hash.keccak256(
                    // TODO:
                    // Bytes.concatenate(messageHash, domainBytes, Bytes.fromHexString("0x02"))))
                    Bytes.concatenate(message, domainBytes, Bytes.fromHexString("0x02"))))
            .toArray();

    BIG xRe = BIG.fromBytes(xReBytes);
    BIG xIm = BIG.fromBytes(xImBytes);
    BIG one = new BIG(1);

    ECP2 point = new ECP2(new FP2(xRe, xIm));
    while (point.is_infinity()) {
      xRe.add(one);
      xRe.norm();
      point = new ECP2(new FP2(xRe, xIm));
    }

    return new G2Point(scaleWithCofactor(normaliseY(point)));
  }

  /**
   * Correct the Y coordinate of a point if necessary in accordance with the Eth2 spec
   *
   * <p>After creating a point from only its X value, there is a choice of two Y values. The Milagro
   * library sometimes returns the wrong one, so we need to correct. The Eth2 spec specifies that we
   * need to choose the Y value with greater imaginary part, or with greater real part iof the
   * imaginaries are equal.
   *
   * @param point the point whose Y coordinate is to be normalised.
   * @return a new point with the correct Y coordinate, which may the original.
   */
  @VisibleForTesting
  static ECP2 normaliseY(ECP2 point) {
    FP2 y = point.getY();
    FP2 yNeg = new FP2(y);
    yNeg.neg();
    if (BIG.comp(y.getB(), yNeg.getB()) < 0
        || ((BIG.comp(y.getB(), yNeg.getB()) == 0) && BIG.comp(y.getA(), yNeg.getA()) < 0)) {
      return new ECP2(point.getX(), yNeg);
    } else {
      return point;
    }
  }

  /**
   * Multiply the point by the group cofactor.
   *
   * <p>Since the group cofactor is extremely large, we do a long multiplication.
   *
   * @param point the point to be scaled
   * @return a scaled point
   */
  @VisibleForTesting
  static ECP2 scaleWithCofactor(ECP2 point) {

    // These are a representation of the G2 cofactor (a 512 bit number)
    String upperHex =
        "0x0000000000000000000000000000000005d543a95414e7f1091d50792876a202cd91de4547085abaa68a205b2e5a7ddf";
    String lowerHex =
        "0x00000000000000000000000000000000a628f1cb4d9e82ef21537e293a6691ae1616ec6e786f0c70cf1c38e31c7238e5";
    String shiftHex =
        "0x000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000";

    BIG upper = BIG.fromBytes(Bytes.fromHexString(upperHex).toArray());
    BIG lower = BIG.fromBytes(Bytes.fromHexString(lowerHex).toArray());
    BIG shift = BIG.fromBytes(Bytes.fromHexString(shiftHex).toArray());

    ECP2 sum = new ECP2(point);
    sum = sum.mul(upper);
    sum = sum.mul(shift);

    ECP2 tmp = new ECP2(point);
    tmp = tmp.mul(lower);

    sum.add(tmp);

    return sum;
  }

  private final ECP2 point;

  /**
   * The following are the flags defined in the Eth2 BLS spec. There are also a2, b2 and c2 flags
   * defined, but these are always zero, so we omit tem here
   */
  // Bit 381 of the imaginary part of X. Equal to ((y_im * 2) / q == 1)
  private final boolean a1;
  // Bit 382 of the imaginary part of X. True only for the point at infinity.
  private final boolean b1;
  // Bit 383 of the imaginary part of X. Always true.
  private final boolean c1;

  private static final int fpPointSize = BIG.MODBYTES;

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
  G2Point(ECP2 point) {
    this(
        point,
        !point.is_infinity() && calculateYFlag(point.getY().getB()),
        point.is_infinity(),
        true);
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
  private G2Point(ECP2 point, boolean a1, boolean b1, boolean c1) {
    checkArgument(isValid(point, a1, b1, c1));
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
    checkArgument(
        bytes.size() == 2 * fpPointSize,
        "Expected %s bytes but received %s",
        2 * fpPointSize,
        bytes.size());
    byte[] xImBytes = bytes.slice(0, fpPointSize).toArray();
    byte[] xReBytes = bytes.slice(fpPointSize, fpPointSize).toArray();

    boolean a = (xImBytes[0] & (byte) (1 << 5)) != 0;
    boolean b = (xImBytes[0] & (byte) (1 << 6)) != 0;
    boolean c = (xImBytes[0] & (byte) (1 << 7)) != 0;
    xImBytes[0] &= (byte) 31;
    checkArgument(
        (xReBytes[0] & (byte) 224) == 0, "The input has non-zero a2, b2 or c2 flag on xRe");

    ECP2 point = new ECP2(new FP2(BIG.fromBytes(xReBytes), BIG.fromBytes(xImBytes)));

    // Did we get the right branch of the sqrt?
    if (!point.is_infinity() && a != calculateYFlag(point.getY().getB())) {
      // We didn't: so choose the other branch of the sqrt.
      FP2 x = point.getX();
      FP2 yneg = point.getY();
      yneg.neg();
      point = new ECP2(x, yneg);
    }

    return new G2Point(point, a, b, c);
  }

  /**
   * Check the validity of a G2 point according to the Eth2 spec
   *
   * @return true if the given point and its flags are valid according to the Eth2 spec
   */
  static boolean isValid(G2Point point) {
    return isValid(point.ecp2Point(), point.a1, point.b1, point.c1);
  }

  /**
   * Check the validity of an ECP2 point and its flags according to the Eth2 spec.
   *
   * @return true if point is consistent with the flags
   */
  @VisibleForTesting
  static boolean isValid(ECP2 point, boolean a1, boolean b1, boolean c1) {
    BIG xRe = point.getX().getA();
    BIG xIm = point.getX().getB();
    BIG yIm = point.getY().getB();

    // Check xIm and xRe are both < q (the field modulus)
    // TODO: Check xIm and xRe are both < q (the field modulus)

    if (!c1) {
      return false;
    }

    // Point at infinity
    if (b1 != point.is_infinity()) {
      return false;
    }
    if (b1) {
      return (!a1 && xRe.iszilch() && xIm.iszilch());
    }

    // Check that we have the right branch for Y
    if (a1 != calculateYFlag(yIm)) {
      return false;
    }

    // Check that both X and Y are on the curve
    ECP2 newPoint = new ECP2(point.getX(), point.getY());
    return point.equals(newPoint);
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

  // Getters used only for testing

  boolean getA1() {
    return a1;
  }

  boolean getB1() {
    return b1;
  }

  boolean getC1() {
    return c1;
  }
}
