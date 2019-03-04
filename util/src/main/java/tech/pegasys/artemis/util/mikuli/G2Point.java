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
import org.apache.milagro.amcl.BLS381.ROM;
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
   * @param message the message to be hashed. This is usually the 32 byte message digest.
   * @param domain the signature domain as defined in the Eth2 spec
   * @return a point from the G2 group representing the message hash
   */
  static G2Point hashToG2(Bytes message, long domain) {
    Security.addProvider(new BouncyCastleProvider());
    Bytes domainBytes = Bytes.ofUnsignedLong(domain);
    Bytes padding = Bytes.wrap(new byte[16]);

    byte[] xReBytes =
        Bytes.concatenate(
                padding,
                Hash.keccak256(
                    Bytes.concatenate(message, domainBytes, Bytes.fromHexString("0x01"))))
            .toArray();
    byte[] xImBytes =
        Bytes.concatenate(
                padding,
                Hash.keccak256(
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

  private static final int fpPointSize = BIG.MODBYTES;

  /** Default constructor creates the point at infinity (the zero point) */
  G2Point() {
    this(new ECP2());
  }

  /**
   * Constructor for point
   *
   * @param point the ec2p point
   */
  G2Point(ECP2 point) {
    this.point = point;
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

    // Serialisation flags as defined in the Eth2 specs
    boolean c1 = true;
    boolean b1 = point.is_infinity();
    boolean a1 = !b1 && calculateYFlag(point.getY().getB());

    byte flags = (byte) ((a1 ? 1 << 5 : 0) | (b1 ? 1 << 6 : 0) | (c1 ? 1 << 7 : 0));
    byte mask = (byte) 31;
    xImBytes[0] &= mask;
    xImBytes[0] |= flags;
    xReBytes[0] &= mask;

    return Bytes.concatenate(Bytes.wrap(xImBytes), Bytes.wrap(xReBytes));
  }

  static G2Point fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 192, "Expected 192 bytes, received %s.", bytes.size());
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

    boolean aIn = (xImBytes[0] & (byte) (1 << 5)) != 0;
    boolean bIn = (xImBytes[0] & (byte) (1 << 6)) != 0;
    boolean cIn = (xImBytes[0] & (byte) (1 << 7)) != 0;
    xImBytes[0] &= (byte) 31;

    if ((xReBytes[0] & (byte) 224) != 0) {
      throw new IllegalArgumentException("The input has non-zero a2, b2 or c2 flag on xRe");
    }

    if (!cIn) {
      throw new IllegalArgumentException("The serialised input does not have the C flag set.");
    }

    if (bIn) {
      if (!aIn && Bytes.wrap(xImBytes).isZero() && Bytes.wrap(xReBytes).isZero()) {
        // This is a correctly formed serialisation of infinity
        return new G2Point();
      } else {
        // The input is malformed
        throw new IllegalArgumentException(
            "The serialised input has B flag set, but A flag is set, or X is non-zero.");
      }
    }

    // Per the spec, we must check that x < q (the curve modulus) for this serialisation to be valid
    // We raise an exception (that should be caught) if this check fails: somebody might feed us
    // faulty input.
    BIG xImBig = BIG.fromBytes(xImBytes);
    BIG xReBig = BIG.fromBytes(xReBytes);
    BIG modulus = new BIG(ROM.Modulus);
    if (BIG.comp(modulus, xReBig) <= 0 || BIG.comp(modulus, xImBig) <= 0) {
      throw new IllegalArgumentException(
          "The deserialised X real or imaginary coordinate is too large.");
    }

    ECP2 point = new ECP2(new FP2(xReBig, xImBig));

    if (point.is_infinity()) {
      throw new IllegalArgumentException("X coordinate is not on the curve.");
    }

    // Did we get the right branch of the sqrt?
    if (!point.is_infinity() && aIn != calculateYFlag(point.getY().getB())) {
      // We didn't: so choose the other branch of the sqrt.
      FP2 x = point.getX();
      FP2 yneg = point.getY();
      yneg.neg();
      point = new ECP2(x, yneg);
    }

    return new G2Point(point);
  }

  ECP2 ecp2Point() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString();
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
    return point.equals(other.point);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        point.getX().getA().norm(),
        point.getY().getA().norm(),
        point.getX().getB().norm(),
        point.getY().getB().norm());
  }
}
