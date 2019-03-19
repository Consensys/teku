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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;

/** This class represents a Signature on G2 */
public final class Signature {

  /**
   * Aggregates list of Signature pairs, returns the signature that corresponds to G2 point at
   * infinity if list is empty
   *
   * @param signatures The list of signatures to aggregate
   * @return Signature
   */
  public static Signature aggregate(List<Signature> signatures) {
    if (signatures.isEmpty()) {
      return new Signature(new G2Point());
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
   */
  public static Signature fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 192, "Expected 192 bytes of input but got %s", bytes.size());
    return new Signature(G2Point.fromBytes(bytes));
  }

  /**
   * Decode a signature from its <em>compressed</em> form serialized representation.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   */
  public static Signature fromBytesCompressed(Bytes bytes) {
    checkArgument(bytes.size() == 96, "Expected 96 bytes of input but got %s", bytes.size());
    return new Signature(G2Point.fromBytesCompressed(bytes));
  }

  /**
   * Create a random signature for testing
   *
   * @return a random, valid signature
   */
  public static Signature random() {
    KeyPair keyPair = KeyPair.random();
    byte[] message = "Hello, world!".getBytes(UTF_8);
    SignatureAndPublicKey sigAndPubKey = BLS12381.sign(keyPair, message, 48);
    return sigAndPubKey.signature();
  }

  /**
   * Create a random signature for testing
   *
   * @param entropy to seed the key pair generation
   * @return a random, valid signature
   */
  public static Signature random(int entropy) {
    KeyPair keyPair = KeyPair.random(entropy);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    SignatureAndPublicKey sigAndPubKey = BLS12381.sign(keyPair, message, 48);

    return sigAndPubKey.signature();
  }

  private final G2Point point;

  /**
   * Construct signature from a given G2 point
   *
   * @param point the G2 point corresponding to the signature
   */
  Signature(G2Point point) {
    this.point = point;
  }

  /**
   * Construct a copy of a signature
   *
   * @param signature the signature to be copied
   */
  Signature(Signature signature) {
    this.point = signature.point;
  }

  /**
   * Combines this signature with another signature, creating a new signature.
   *
   * @param signature the signature to combine with
   * @return a new signature as combination of both signatures
   */
  public Signature combine(Signature signature) {
    return new Signature(point.add(signature.point));
  }

  /**
   * Signature serialization
   *
   * @return byte array representation of the signature, not null
   */
  public Bytes toBytes() {
    return point.toBytes();
  }

  /**
   * Signature serialization to compressed form
   *
   * @return byte array representation of the signature, not null
   */
  public Bytes toBytesCompressed() {
    return point.toBytesCompressed();
  }

  @Override
  public String toString() {
    return "Signature [ecp2Point=" + point.toString() + "]";
  }

  @Override
  public int hashCode() {
    return point.hashCode();
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
