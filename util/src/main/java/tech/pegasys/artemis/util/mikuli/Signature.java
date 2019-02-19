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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.milagro.amcl.BLS381.BIG.comp;

import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;

/** This class represents a Signature on G2 */
public final class Signature {

  /**
   * Aggregates list of Signature pairs
   *
   * @param signatures The list of signatures to aggregate, not null
   * @throws IllegalArgumentException if parameter list is empty
   * @return Signature, not null
   */
  public static Signature aggregate(List<Signature> signatures) {
    if (signatures.isEmpty()) {
      throw new IllegalArgumentException("Parameter list is empty");
    }
    return signatures.stream().reduce(Signature::combine).get();
  }

  /**
   * Decode a signature from its serialized representation.
   *
   * <p>Note that this uses uncompressed form, and requires 192 bytes of input.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   * @throws IllegalArgumentException if the wrong number of bytes is provided
   */
  public static Signature decode(Bytes bytes) {
    if (bytes.size() != 192) {
      throw new IllegalArgumentException("Signatures need 192 bytes of input");
    }
    G2Point point = G2Point.fromBytes(bytes);
    return new Signature(point);
  }

  /**
   * Decode a signature from its <em>compressed</em> serialized representation.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   * @throws IllegalArgumentException if the wrong number of bytes is provided
   */
  public static Signature decodeCompressed(Bytes bytes) {
    if (bytes.size() != 96) {
      throw new IllegalArgumentException("Signatures need 192 bytes of input");
    }

    Bytes x1 = bytes.slice(0, 48);
    Bytes x2 = bytes.slice(48, 48);
    FP2 x = new FP2(BIG.fromBytes(x1.toArray()), BIG.fromBytes(x2.toArray()));

    // Construct a Y point based on this X. This may be one of two possibilities. The Signature
    // constructor takes care of choosing the correct one.
    G2Point point = new G2Point(new ECP2(x));

    return new Signature(point);
  }

  /**
   * Normalise the signature to have the correct Y value as defined in the spec
   *
   * <p>For any given X value, we have a choice of two Y values. Milagro returns one or the other of
   * them arbitrarily.
   *
   * <p>We need to choose the one with greater imaginary part, or greater real if imaginaries are
   * the same.
   *
   * @param point the point that needs the correct Y coord setting
   * @return the signature
   */
  private static G2Point normalise(G2Point point) {
    FP2 x = point.ecp2Point().getX();
    FP2 y1 = point.ecp2Point().getY();

    // The alternative Y value is the additive inverse of the one we have
    FP2 y2 = new FP2(y1);
    y2.neg();

    FP2 y;
    if (comp(y1.getB(), y2.getB()) == 0) {
      // Imaginary parts equal, so choose the one with greater real part
      y = (comp(y1.getA(), y2.getA()) > 0) ? y1 : y2;
    } else {
      // Choose the one with greater imaginary part
      y = (comp(y1.getB(), y2.getB()) > 0) ? y1 : y2;
    }

    return new G2Point(new ECP2(x, y));
  }

  /**
   * Create a random signature for testing
   *
   * @return the signature
   */
  public static Signature random() {
    KeyPair keyPair = KeyPair.random();
    byte[] message = "Hello, world!".getBytes(UTF_8);
    SignatureAndPublicKey sigAndPubKey = BLS12381.sign(keyPair, message, 48);

    // System.out.println(sigAndPubKey.signature());
    return sigAndPubKey.signature();
  }

  private final G2Point point;

  Signature(G2Point point) {
    this.point = normalise(point);
  }

  /**
   * Combines this signature with another signature, creating a new signature.
   *
   * @param signature the signature to combine with
   * @return a new signature as combination of both signatures.
   */
  public Signature combine(Signature signature) {
    return new Signature(point.add(signature.point));
  }

  /**
   * Signature serialization
   *
   * @return byte array representation of the signature, not null
   */
  public Bytes encode() {
    return point.toBytes();
  }

  @Override
  public String toString() {
    return "Signature [ecpPoint=" + point.toString() + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((point == null) ? 0 : point.hashCode());
    return result;
  }

  G2Point g2Point() {
    return point;
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Signature)) {
      return false;
    }
    Signature other = (Signature) obj;
    return point.equals(other.point);
  }
}
