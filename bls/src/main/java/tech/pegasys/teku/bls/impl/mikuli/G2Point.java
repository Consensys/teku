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

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Random;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.DeserializeException;
import tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve;

/**
 * G2 is a subgroup of an elliptic curve whose points are elements of the finite field Fp^2. The
 * curve is defined by: y^2 = x^3 + 4(1 + i).
 */
public final class G2Point implements Group<G2Point> {

  /**
   * Generate a random point in G2 from a seed value. The same seed value gives the same point.
   *
   * @param seed a seed value
   * @return a random point in G2
   */
  public static G2Point random(long seed) {
    return random(new Random(seed));
  }

  /**
   * Generate a random point in G2 given an RNG. Uses hashToG2 to hash random bytes.
   *
   * @param rng the RNG to use
   * @return a random point in G2
   */
  private static G2Point random(Random rng) {
    return new G2Point(HashToCurve.hashToG2(Bytes.random(32, rng)));
  }

  public static G2Point hashToG2(Bytes message) {
    return new G2Point(HashToCurve.hashToG2(message));
  }

  private final ECP2 point;

  private static final int fpPointSize = BIG.MODBYTES;

  /** Default constructor creates the point at infinity (the zero point) */
  public G2Point() {
    this(new ECP2());
  }

  /**
   * Constructor for point
   *
   * @param point the ec2p point
   */
  public G2Point(ECP2 point) {
    this.point = point;
  }

  @Override
  public G2Point add(G2Point other) {
    ECP2 newPoint = new ECP2(point);
    newPoint.add(other.point);
    return new G2Point(newPoint);
  }

  @Override
  public G2Point mul(Scalar scalar) {
    return new G2Point(point.mul(scalar.value()));
  }

  public ECP2 getPoint() {
    return point;
  }

  public Bytes toBytes() {
    byte[] bytes = new byte[4 * fpPointSize];
    point.toBytes(bytes);
    return Bytes.wrap(bytes);
  }

  /**
   * Serialize the point into compressed form
   *
   * <p>In compressed form we (a) pass only the X coordinate, and (b) include flags in the higher
   * order bits per the Eth2 BLS spec. We also take care about encoding the real and imaginary parts
   * in the correct order: [Im, Re]
   *
   * <p>The standard follows the ZCash format for serialization documented here:
   * https://github.com/zkcrypto/pairing/blob/master/src/bls12_381/README.md#serialization
   *
   * @return the serialized compressed form of the point
   */
  @VisibleForTesting
  public Bytes toBytesCompressed() {
    byte[] xReBytes = new byte[fpPointSize];
    byte[] xImBytes = new byte[fpPointSize];
    point.getX().getA().toBytes(xReBytes);
    point.getX().getB().toBytes(xImBytes);

    // Serialization flags as defined in the documentation
    boolean b1 = point.is_infinity();
    boolean a1 = !b1 && Util.calculateYFlag(point.getY().getB());

    // c1 is always true for compressed points
    byte flags = (byte) ((4 | (b1 ? 2 : 0) | (a1 ? 1 : 0)) << 5);
    byte mask = (byte) 31;
    xImBytes[0] &= mask;
    xImBytes[0] |= flags;
    xReBytes[0] &= mask;

    return Bytes.concatenate(Bytes.wrap(xImBytes), Bytes.wrap(xReBytes));
  }

  public static G2Point fromBytes(Bytes bytes) {
    if (bytes.size() != 4 * fpPointSize) {
      throw new DeserializeException(
          "Expected " + 4 * fpPointSize + " bytes but received " + bytes.size());
    }
    return new G2Point(ECP2.fromBytes(bytes.toArrayUnsafe()));
  }

  /**
   * Deserialize the point from compressed form.
   *
   * <p>The standard follows the ZCash format for serialization documented here:
   * https://github.com/zkcrypto/pairing/blob/master/src/bls12_381/README.md#serialization
   *
   * @param bytes the compressed serialized form of the point
   * @return the point
   */
  public static G2Point fromBytesCompressed(Bytes bytes) {
    if (bytes.size() != 2 * fpPointSize) {
      throw new DeserializeException(
          "Expected " + 2 * fpPointSize + " bytes but received " + bytes.size());
    }
    byte[] xImBytes = bytes.slice(0, fpPointSize).toArray();
    byte[] xReBytes = bytes.slice(fpPointSize, fpPointSize).toArray();

    boolean aIn = (xImBytes[0] & (byte) (1 << 5)) != 0;
    boolean bIn = (xImBytes[0] & (byte) (1 << 6)) != 0;
    boolean cIn = (xImBytes[0] & (byte) (1 << 7)) != 0;
    xImBytes[0] &= (byte) 31;

    if ((xReBytes[0] & (byte) 224) != 0) {
      throw new DeserializeException("The input has non-zero a2, b2 or c2 flag on xRe");
    }

    if (!cIn) {
      throw new DeserializeException("The serialized input does not have the C flag set.");
    }

    if (bIn) {
      if (!aIn && Bytes.wrap(xImBytes).isZero() && Bytes.wrap(xReBytes).isZero()) {
        // This is a correctly formed serialization of infinity
        return new G2Point();
      } else {
        // The input is malformed
        throw new DeserializeException(
            "The serialized input has B flag set, but A flag is set, or X is non-zero.");
      }
    }

    // We must check that x < q (the curve modulus) for this serialization to be valid
    // We raise an exception (that should be caught) if this check fails: somebody might feed us
    // faulty input.
    BIG xImBig = BIG.fromBytes(xImBytes);
    BIG xReBig = BIG.fromBytes(xReBytes);
    BIG modulus = new BIG(ROM.Modulus);
    if (BIG.comp(modulus, xReBig) <= 0 || BIG.comp(modulus, xImBig) <= 0) {
      throw new DeserializeException(
          "The deserialized X real or imaginary coordinate is too large.");
    }

    ECP2 point = new ECP2(new FP2(xReBig, xImBig));

    if (point.is_infinity()) {
      throw new DeserializeException("X coordinate is not on the curve.");
    }

    if (!isInGroup(point)) {
      throw new DeserializeException("The deserialized point is not in the G2 subgroup.");
    }

    // Did we get the right branch of the sqrt?
    if (aIn != Util.calculateYFlag(point.getY().getB())) {
      // We didn't: so choose the other branch of the sqrt.
      FP2 x = point.getX();
      FP2 yneg = point.getY();
      yneg.neg();
      point = new ECP2(x, yneg);
    }

    return new G2Point(point);
  }

  /**
   * Verify that the given point is in the correct subgroup for G2.
   *
   * @param point The elliptic curve point
   * @return True if the point is in G2; false otherwise
   */
  public static boolean isInGroup(ECP2 point) {
    return HashToCurve.isInGroupG2(point);
  }

  public ECP2 ecp2Point() {
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
    return Objects.hash(point.toString());
  }
}
