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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;

/** This class represents a Signature on G2 */
public class MikuliSignature implements Signature {

  private static final int COMPRESSED_SIG_SIZE = 96;
  private static final int UNCOMPRESSED_SIG_SIZE = 192;
  private static final G1Point g1GeneratorNeg = Util.g1Generator.neg();

  /**
   * Aggregates a list of Signatures, returning the signature that corresponds to G2 point at
   * infinity if list is empty.
   *
   * @param signatures The list of signatures to aggregate
   * @return Signature
   */
  public static MikuliSignature aggregate(List<MikuliSignature> signatures) {
    return aggregate(signatures.stream());
  }

  /**
   * Aggregates a stream of Signatures, returning the signature that corresponds to G2 point at
   * infinity if list is empty.
   *
   * @param signatures The stream of signatures to aggregate
   * @return Signature
   */
  public static MikuliSignature aggregate(Stream<MikuliSignature> signatures) {
    return signatures
        .reduce(MikuliSignature::combine)
        .orElseGet(() -> new MikuliSignature(new G2Point()));
  }

  /**
   * Decode a signature from its serialized representation.
   *
   * <p>Note that this uses uncompressed form, and requires 192 bytes of input.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   */
  public static MikuliSignature fromBytes(Bytes bytes) {
    return new MikuliSignature(bytes);
  }

  /**
   * Decode a signature from its <em>compressed</em> form serialized representation.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   */
  public static MikuliSignature fromBytesCompressed(Bytes bytes) {
    checkArgument(
        bytes.size() == COMPRESSED_SIG_SIZE,
        "Expected " + COMPRESSED_SIG_SIZE + " bytes of input but got %s",
        bytes.size());
    return new MikuliSignature(bytes);
  }

  static MikuliSignature fromSignature(Signature signature) {
    if (signature instanceof MikuliSignature) {
      return (MikuliSignature) signature;
    } else {
      return MikuliSignature.fromBytesCompressed(signature.toBytesCompressed());
    }
  }

  /**
   * Create a random signature for testing
   *
   * @param entropy to seed the key pair generation
   * @return a random, valid signature
   */
  public static MikuliSignature random(int entropy) {
    MikuliKeyPair keyPair = MikuliKeyPair.random(entropy);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    return MikuliBLS12381.sign(keyPair.getSecretKey(), Bytes.wrap(message));
  }

  // Sometimes we are dealing with random, invalid signature points, e.g. when testing.
  // Let's only interpret the raw data into a point when necessary to do so.
  private final Bytes rawData;
  private final Supplier<G2Point> point;

  /**
   * Construct signature from a given G2 point.
   *
   * @param point the G2 point corresponding to the signature
   */
  public MikuliSignature(G2Point point) {
    this.rawData = point.toBytes();
    this.point = () -> point;
  }

  /**
   * Construct signature from provided Bytes.
   *
   * @param rawData Bytes that may or may not correspond to a G2 point
   */
  public MikuliSignature(Bytes rawData) {
    this.rawData = rawData;
    this.point = Suppliers.memoize(() -> parseSignatureBytes(this.rawData));
  }

  /**
   * Construct a copy of a signature.
   *
   * @param signature the signature to be copied
   */
  public MikuliSignature(MikuliSignature signature) {
    this.rawData = signature.rawData;
    this.point = signature.point;
  }

  @Override
  public boolean verify(List<PublicKeyMessagePair> keysToMessages) {
    return MikuliBLS12381.aggregateVerify(
        keysToMessages.stream()
            .map(km -> MikuliPublicKey.fromPublicKey(km.getPublicKey()))
            .collect(toList()),
        keysToMessages.stream().map(PublicKeyMessagePair::getMessage).collect(toList()),
        this);
  }

  @Override
  public boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return MikuliBLS12381.fastAggregateVerify(
        publicKeys.stream().map(MikuliPublicKey::fromPublicKey).collect(toList()), message, this);
  }

  private G2Point parseSignatureBytes(Bytes signatureBytes) {
    if (signatureBytes.size() == COMPRESSED_SIG_SIZE) {
      return G2Point.fromBytesCompressed(signatureBytes);
    } else if (signatureBytes.size() == UNCOMPRESSED_SIG_SIZE) {
      return G2Point.fromBytes(signatureBytes);
    }
    throw new RuntimeException(
        "Expected either "
            + COMPRESSED_SIG_SIZE
            + " or "
            + UNCOMPRESSED_SIG_SIZE
            + " bytes for signature, but found "
            + signatureBytes.size());
  }

  /**
   * Verify that this signature is correct for the given public key and G2Point.
   *
   * @param publicKey The public key, not null
   * @param hashInG2 The G2 point corresponding to the message data to verify, not null
   * @return True if the verification is successful, false otherwise
   */
  public boolean verify(MikuliPublicKey publicKey, G2Point hashInG2) {
    try {
      GTPoint e = AtePairing.pair2(publicKey.g1Point(), hashInG2, g1GeneratorNeg, point.get());
      return e.isunity();
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Verify that this signature is correct for the given lists of public keys and G2Points.
   *
   * @param publicKeys The list of public keys, not empty, not null
   * @param hashesInG2 The list of G2 point corresponding to the messages to verify, not null
   * @return True if the verification is successful, false otherwise
   */
  public boolean aggregateVerify(List<MikuliPublicKey> publicKeys, List<G2Point> hashesInG2) {
    checkArgument(
        publicKeys.size() == hashesInG2.size(),
        "List of public keys and list of messages differ in length");
    checkArgument(publicKeys.size() > 0, "List of public keys is empty");
    try {
      GTPoint gt1 = AtePairing.pair(publicKeys.get(0).g1Point(), hashesInG2.get(0));
      for (int i = 1; i < publicKeys.size(); i++) {
        gt1 = gt1.mul(AtePairing.pair(publicKeys.get(i).g1Point(), hashesInG2.get(i)));
      }
      GTPoint gt2 = AtePairing.pair(Util.g1Generator, point.get());
      return gt2.equals(gt1);
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Combines this signature with another signature, creating a new signature.
   *
   * @param signature the signature to combine with
   * @return a new signature as combination of both signatures
   */
  public MikuliSignature combine(MikuliSignature signature) {
    return new MikuliSignature(point.get().add(signature.point.get()));
  }

  /**
   * Signature serialization
   *
   * @return byte array representation of the signature, not null
   */
  @Override
  public Bytes toBytesUncompressed() {
    return (rawData.size() == UNCOMPRESSED_SIG_SIZE) ? rawData : point.get().toBytes();
  }

  /**
   * Signature serialization to compressed form
   *
   * @return byte array representation of the signature, not null
   */
  @Override
  public Bytes toBytesCompressed() {
    return (rawData.size() == COMPRESSED_SIG_SIZE) ? rawData : point.get().toBytesCompressed();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }

  @Override
  public int hashCode() {
    try {
      return point.get().hashCode();
    } catch (final IllegalArgumentException e) {
      // Invalid point so only equal if it has the same raw data, hence use that hashCode.
      return rawData.hashCode();
    }
  }

  @VisibleForTesting
  public G2Point g2Point() {
    return point.get();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MikuliSignature)) {
      return false;
    }
    MikuliSignature other = (MikuliSignature) obj;
    if (rawData.size() == other.rawData.size() && rawData.equals(other.rawData)) {
      return true;
    }
    try {
      return point.get().equals(other.point.get());
    } catch (final IllegalArgumentException e) {
      // Invalid points are only equal if they have the exact some data.
      return false;
    }
  }
}
