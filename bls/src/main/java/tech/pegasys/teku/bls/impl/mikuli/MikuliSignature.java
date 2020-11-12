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
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve.hashToG2;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve;

/** This class represents a Signature on G2 */
public class MikuliSignature implements Signature {

  private static final int COMPRESSED_SIG_SIZE = 96;
  private static final G1Point g1GeneratorNeg = Util.g1Generator.neg();

  /**
   * Aggregates a list of Signatures, returning the signature that corresponds to G2 point at
   * infinity if list is empty.
   *
   * @param signatures The list of signatures to aggregate
   * @return Signature
   */
  public static MikuliSignature aggregate(List<MikuliSignature> signatures) {
    return signatures.stream()
        .reduce(MikuliSignature::combine)
        .orElseGet(() -> new MikuliSignature(new G2Point()));
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
    return new MikuliSignature(G2Point.fromBytesCompressed(bytes));
  }

  static MikuliSignature fromSignature(Signature signature) {
    if (signature instanceof MikuliSignature) {
      return (MikuliSignature) signature;
    } else {
      return MikuliSignature.fromBytesCompressed(signature.toBytesCompressed());
    }
  }

  private final G2Point point;

  /**
   * Construct signature from a given G2 point.
   *
   * @param point the G2 point corresponding to the signature
   */
  public MikuliSignature(G2Point point) {
    this.point = point;
  }

  /**
   * Construct a copy of a signature.
   *
   * @param signature the signature to be copied
   */
  public MikuliSignature(MikuliSignature signature) {
    this.point = signature.point;
  }

  @Override
  public boolean verify(List<PublicKeyMessagePair> keysToMessages) {
    if (keysToMessages.stream().map(PublicKeyMessagePair::getMessage).distinct().count()
        != keysToMessages.size()) {
      // duplicate messages found
      return false;
    }

    List<G2Point> hashesInG2 =
        keysToMessages.stream()
            .map(km -> new G2Point(hashToG2(km.getMessage())))
            .collect(Collectors.toList());
    return aggregateVerify(
        keysToMessages.stream()
            .map(km -> MikuliPublicKey.fromPublicKey(km.getPublicKey()))
            .collect(Collectors.toList()),
        hashesInG2);
  }

  @Override
  public boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return verify(MikuliPublicKey.aggregate(publicKeys), message, HashToCurve.ETH2_DST);
  }

  @Override
  public boolean verify(PublicKey publicKey, Bytes message, Bytes dst) {
    G2Point hashInGroup2 = new G2Point(hashToG2(message, dst));
    return verify(MikuliPublicKey.fromPublicKey(publicKey), hashInGroup2);
  }

  /**
   * Verify that this signature is correct for the given public key and G2Point.
   *
   * @param publicKey The public key, not null
   * @param hashInG2 The G2 point corresponding to the message data to verify, not null
   * @return True if the verification is successful, false otherwise
   */
  private boolean verify(MikuliPublicKey publicKey, G2Point hashInG2) {
    if (publicKey.isInfinity()) {
      return false;
    }

    try {
      GTPoint e = AtePairing.pair2(publicKey.g1Point(), hashInG2, g1GeneratorNeg, point);
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
  private boolean aggregateVerify(List<MikuliPublicKey> publicKeys, List<G2Point> hashesInG2) {
    checkArgument(
        publicKeys.size() == hashesInG2.size(),
        "List of public keys and list of messages differ in length");
    checkArgument(publicKeys.size() > 0, "List of public keys is empty");

    if (publicKeys.stream().anyMatch(MikuliPublicKey::isInfinity)) {
      return false;
    }

    try {
      GTPoint gt1 = AtePairing.pair(publicKeys.get(0).g1Point(), hashesInG2.get(0));
      for (int i = 1; i < publicKeys.size(); i++) {
        gt1 = gt1.mul(AtePairing.pair(publicKeys.get(i).g1Point(), hashesInG2.get(i)));
      }
      GTPoint gt2 = AtePairing.pair(Util.g1Generator, point);
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
    return new MikuliSignature(point.add(signature.point));
  }

  /**
   * Signature serialization to compressed form
   *
   * @return byte array representation of the signature, not null
   */
  @Override
  public Bytes toBytesCompressed() {
    return point.toBytesCompressed();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }

  @Override
  public int hashCode() {
    return point.hashCode();
  }

  @VisibleForTesting
  public G2Point g2Point() {
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
    try {
      MikuliSignature other = MikuliSignature.fromSignature((Signature) obj);
      return point.equals(other.point);
    } catch (final IllegalArgumentException e) {
      // Invalid points are only equal if they have the exact some data.
      return false;
    }
  }
}
