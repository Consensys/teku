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

import java.util.Objects;
import java.util.Random;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.DeserializeException;

/**
 * G1 is a subgroup of an elliptic curve whose points are elements of the finite field Fp. The curve
 * is defined by: y^2 = x^3 + 4.
 */
public final class G1Point implements Group<G1Point> {

  /**
   * Generate a random point on the curve from a seed value. The same seed value gives the same
   * point.
   *
   * @param seed the seed value
   * @return a random point on the curve.
   */
  public static G1Point random(long seed) {
    return random(new Random(seed));
  }

  private static G1Point random(Random rng) {
    ECP point;
    byte[] xBytes = new byte[48];

    // Repeatedly choose random X coords until one is on the curve. This generally takes only a
    // couple of attempts.
    do {
      rng.nextBytes(xBytes);
      point = new ECP(BIG.fromBytes(xBytes));
    } while (point.is_infinity());

    // Multiply by the cofactor to ensure that we end up on G1
    return new G1Point(scaleWithCofactor(point));
  }

  public static G1Point fromBytes(Bytes bytes) {
    if (bytes.size() != 49) {
      throw new DeserializeException("Expected 49 bytes but received " + bytes.size());
    }
    return new G1Point(ECP.fromBytes(bytes.toArrayUnsafe()));
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
  public static G1Point fromBytesCompressed(Bytes bytes) {
    if (bytes.size() != fpPointSize) {
      throw new DeserializeException(
          "Expected " + fpPointSize + " bytes but received " + bytes.size());
    }
    byte[] xBytes = bytes.toArray();

    boolean aIn = (xBytes[0] & (byte) (1 << 5)) != 0;
    boolean bIn = (xBytes[0] & (byte) (1 << 6)) != 0;
    boolean cIn = (xBytes[0] & (byte) (1 << 7)) != 0;
    xBytes[0] &= (byte) 31;

    if (!cIn) {
      throw new DeserializeException("The serialized input does not have the C flag set.");
    }

    if (bIn) {
      if (!aIn && Bytes.wrap(xBytes).isZero()) {
        // This is a correctly formed serialization of infinity
        return new G1Point();
      } else {
        // The input is malformed
        throw new DeserializeException(
            "The serialized input has B flag set, but A flag is set, or X is non-zero.");
      }
    }

    // We must check that x < q (the curve modulus) for this serialization to be valid
    // We raise an exception (that should be caught) if this check fails: somebody might feed us
    // faulty input.
    BIG xBig = BIG.fromBytes(xBytes);
    BIG modulus = new BIG(ROM.Modulus);
    if (BIG.comp(modulus, xBig) <= 0) {
      throw new DeserializeException("X coordinate is too large.");
    }

    ECP point = new ECP(xBig);

    if (point.is_infinity()) {
      throw new DeserializeException("X coordinate is not on the curve.");
    }

    if (!isInGroup(point)) {
      throw new DeserializeException("The deserialized point is not in the G1 subgroup.");
    }

    // Did we get the right branch of the sqrt?
    if (aIn != Util.calculateYFlag(point.getY())) {
      // We didn't: so choose the other branch of the sqrt.
      FP x = new FP(point.getX());
      FP yneg = new FP(point.getY());
      yneg.neg();
      point = new ECP(x.redc(), yneg.redc());
    }

    return new G1Point(point);
  }

  /**
   * Multiply the point by the group cofactor.
   *
   * @param point the point to be scaled
   * @return a scaled point
   */
  public static ECP scaleWithCofactor(ECP point) {

    // The G1 cofactor
    String cofactorHex =
        "0x0000000000000000000000000000000000000000000000000000000000000000396c8c005555e1568c00aaab0000aaab";

    BIG cofactor = BIG.fromBytes(Bytes.fromHexString(cofactorHex).toArray());

    return (point.mul(cofactor));
  }

  private final ECP point;

  private static final int fpPointSize = BIG.MODBYTES;

  /** Default constructor creates the point at infinity (the zero point) */
  public G1Point() {
    this(new ECP());
  }

  /**
   * Constructor for point
   *
   * @param point the ecp point
   */
  public G1Point(ECP point) {
    this.point = point;
  }

  @Override
  public G1Point add(G1Point other) {
    ECP newPoint = new ECP(point);
    newPoint.add(other.point);
    return new G1Point(newPoint);
  }

  @Override
  public G1Point mul(Scalar scalar) {
    return new G1Point(point.mul(scalar.value()));
  }

  public G1Point neg() {
    ECP newPoint = new ECP(point);
    newPoint.neg();
    return new G1Point(newPoint);
  }

  public ECP getPoint() {
    return point;
  }

  public Bytes toBytes() {
    // Size of the byte array representing compressed ECP point for BLS12-381 is
    // 49 bytes in milagro
    // size of the point = 48 bytes
    // meta information (parity bit, curve type etc) = 1 byte
    byte[] bytes = new byte[fpPointSize + 1];
    point.toBytes(bytes, true);
    return Bytes.wrap(bytes);
  }

  /**
   * Serialize the point into compressed form.
   *
   * <p>In compressed form we (a) pass only the X coordinate, and (b) include flags in the higher
   * order bits per the Eth2 BLS spec.
   *
   * <p>The standard follows the ZCash format for serialization documented here:
   * https://github.com/zkcrypto/pairing/blob/master/src/bls12_381/README.md#serialization
   *
   * @return the serialized compressed form of the point
   */
  public Bytes toBytesCompressed() {
    byte[] xBytes = new byte[fpPointSize];
    point.getX().toBytes(xBytes);

    // Serialization flags as defined in the documentation
    boolean b = point.is_infinity();
    boolean a = !b && Util.calculateYFlag(point.getY());

    // c is always true for compressed points
    byte flags = (byte) ((4 | (b ? 2 : 0) | (a ? 1 : 0)) << 5);
    byte mask = (byte) 31;
    xBytes[0] &= mask;
    xBytes[0] |= flags;

    return Bytes.wrap(xBytes);
  }

  /**
   * Verify that the given point is in the correct subgroup for G1 by multiplying by the group
   * order.
   *
   * <p>There is a potentially more efficient way to do this described in
   * https://eprint.iacr.org/2019/814.pdf
   *
   * @param point The elliptic curve point
   * @return True if the point is in G2; false otherwise
   */
  public static boolean isInGroup(ECP point) {
    ECP orderCheck = point.mul(new BIG(ROM.CURVE_Order));
    return orderCheck.is_infinity();
  }

  public ECP ecpPoint() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(point.toString());
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
