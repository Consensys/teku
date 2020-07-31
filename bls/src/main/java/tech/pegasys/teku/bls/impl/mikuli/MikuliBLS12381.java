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

import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve.hashToG2;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.KeyPair;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;

/*
 * (Heavily) adapted from the ConsenSys/mikuli (Apache 2 License) implementation:
 * https://github.com/ConsenSys/mikuli/blob/master/src/main/java/net/consensys/mikuli/crypto/*.java
 */

/**
 * This Boneh-Lynn-Shacham (BLS) signature implementation is constructed from a pairing friendly
 * BLS12-381 elliptic curve. It implements a subset of the functions from the proposed IETF
 * standard.
 *
 * <p>The basic reference is https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-02, but note
 * that this implementation deviates from the standard in some edge cases (such as aggregating empty
 * lists of signatures, or verifying with empty lists of public keys). Use the tech.pegasys.teku.bls
 * for a fully conformant implementation.
 *
 * <p>This class depends upon the Apache Milagro library being available. See
 * https://milagro.apache.org.
 *
 * <p>Apache Milagro can be included using the gradle dependency
 * 'org.miracl.milagro.amcl:milagro-crypto-java'.
 */
public class MikuliBLS12381 implements BLS12381 {

  public static final MikuliBLS12381 INSTANCE = new MikuliBLS12381();
  private static final long MAX_BATCH_VERIFY_RANDOM_MULTIPLIER = Long.MAX_VALUE;

  private static Random getRND() {
    // Milagro RAND has some issues with generating 'small' random numbers
    // and is not thread safe
    // Using non-secure random due to the JDK Linux secure random issue:
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6521844
    // A potential attack here has a very limited application and is not feasible
    // Thus using non-secure random doesn't significantly mitigate the security
    return ThreadLocalRandom.current();
  }

  protected MikuliBLS12381() {}

  @Override
  public KeyPair generateKeyPair(Random random) {
    MikuliKeyPair mikuliKeyPair = MikuliKeyPair.random(random);
    return new KeyPair(mikuliKeyPair.getSecretKey(), mikuliKeyPair.getPublicKey());
  }

  @Override
  public PublicKey publicKeyFromCompressed(Bytes48 compressedPublicKeyBytes) {
    return MikuliPublicKey.fromBytesCompressed(compressedPublicKeyBytes);
  }

  @Override
  public MikuliSignature signatureFromCompressed(Bytes compressedSignatureBytes) {
    return MikuliSignature.fromBytesCompressed(compressedSignatureBytes);
  }

  @Override
  public SecretKey secretKeyFromBytes(Bytes32 secretKeyBytes) {
    return MikuliSecretKey.fromBytes(Bytes48.leftPad(secretKeyBytes));
  }

  @Override
  public PublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys) {
    return MikuliPublicKey.aggregate(
        publicKeys.stream().map(MikuliPublicKey::fromPublicKey).collect(Collectors.toList()));
  }

  @Override
  public MikuliSignature aggregateSignatures(List<? extends Signature> signatures) {
    return MikuliSignature.aggregate(
        signatures.stream().map(MikuliSignature::fromSignature).collect(Collectors.toList()));
  }

  /**
   * Generates a Signature from a private key and message.
   *
   * @param secretKey The secret key, not null
   * @param message The message to sign, not null
   * @return The Signature, not null
   */
  public static MikuliSignature sign(MikuliSecretKey secretKey, Bytes message) {
    G2Point hashInGroup2 = new G2Point(hashToG2(message));
    return new MikuliSignature(secretKey.sign(hashInGroup2));
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @return True if the verification is successful, false otherwise.
   */
  public static boolean verify(
      MikuliPublicKey publicKey, Bytes message, MikuliSignature signature) {
    return coreVerify(publicKey, message, signature);
  }

  /**
   * Aggregates a list of signatures into a single signature using BLS magic.
   *
   * @param signatures the list of signatures to be aggregated
   * @return the aggregated signature
   */
  public static MikuliSignature aggregate(List<MikuliSignature> signatures) {
    return new MikuliSignature(MikuliSignature.aggregate(signatures));
  }

  /**
   * Aggregates a stream of signatures into a single signature using BLS magic.
   *
   * @param signatures the stream of signatures to be aggregated
   * @return the aggregated signature
   */
  public static MikuliSignature aggregate(Stream<MikuliSignature> signatures) {
    return new MikuliSignature(MikuliSignature.aggregate(signatures));
  }

  /**
   * Verifies an aggregate signature against a list of distinct messages using the list of public
   * keys.
   *
   * @param publicKeys The list of public keys, not null
   * @param messages The list of messages to verify, all distinct, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean aggregateVerify(
      List<MikuliPublicKey> publicKeys, List<Bytes> messages, MikuliSignature signature) {
    // Check that there are no duplicate messages
    Set<Bytes> set = new HashSet<>();
    for (Bytes message : messages) {
      if (!set.add(message)) return false;
    }
    return coreAggregateVerify(publicKeys, messages, signature);
  }

  /**
   * Verifies an aggregate signature against a message using the list of public keys.
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean fastAggregateVerify(
      List<MikuliPublicKey> publicKeys, Bytes message, MikuliSignature signature) {
    return coreVerify(MikuliPublicKey.aggregate(publicKeys), message, signature);
  }

  /**
   * The CoreVerify algorithm checks that a signature is valid for the octet string message under
   * the public key publicKey.
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean coreVerify(
      MikuliPublicKey publicKey, Bytes message, MikuliSignature signature) {
    G2Point hashInGroup2 = new G2Point(hashToG2(message));
    return signature.verify(publicKey, hashInGroup2);
  }

  /**
   * Verifies an aggregate signature against a list of distinct messages using the list of public
   * keys.
   *
   * @param publicKeys The list of public keys, not null
   * @param messages The list of messages to verify, all distinct, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean coreAggregateVerify(
      List<MikuliPublicKey> publicKeys, List<Bytes> messages, MikuliSignature signature) {
    List<G2Point> hashesInG2 =
        messages.stream().map(m -> new G2Point(hashToG2(m))).collect(Collectors.toList());
    return signature.aggregateVerify(publicKeys, hashesInG2);
  }

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>For above batch verification method pre-calculates and returns two values: <code>S * r
   * </code> and <code>e(M * r, P)</code>
   *
   * @return the pair of values above in an opaque instance
   */
  @Override
  public BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature) {
    G2Point sigG2Point;
    G2Point msgG2Point;

    List<MikuliPublicKey> mikuliPublicKeys =
        publicKeys.stream().map(MikuliPublicKey::fromPublicKey).collect(Collectors.toList());
    MikuliSignature mikuliSignature = MikuliSignature.fromSignature(signature);

    if (index == 0) {
      // optimization: we may omit multiplication of a single component (i.e. multiplier is 1)
      // let it be the component with index 0
      sigG2Point = mikuliSignature.g2Point();
      msgG2Point = G2Point.hashToG2(message);
    } else {
      Scalar randomMult = nextBatchRandomMultiplier();
      sigG2Point = mikuliSignature.g2Point().mul(randomMult);
      msgG2Point = G2Point.hashToG2(message).mul(randomMult);
    }

    GTPoint pair =
        AtePairing.pairNoExp(MikuliPublicKey.aggregate(mikuliPublicKeys).g1Point(), msgG2Point);

    return new MukuliBatchSemiAggregate(sigG2Point, pair);
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<? extends PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<? extends PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2) {
    return prepareBatchVerify2(
        index,
        publicKeys1.stream().map(MikuliPublicKey::fromPublicKey).collect(Collectors.toList()),
        message1,
        MikuliSignature.fromSignature(signature1),
        publicKeys2.stream().map(MikuliPublicKey::fromPublicKey).collect(Collectors.toList()),
        message2,
        MikuliSignature.fromSignature(signature2));
  }

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Slightly more efficient variant of {@link #prepareBatchVerify(int, List, Bytes, Signature)}
   * when 2 signatures are aggregated with a faster ate2 pairing
   *
   * <p>For above batch verification method pre-calculates and returns two values: <code>
   * S1 * r1 + S2 * r2</code> and <code>e(M1 * r1, P1) * e(M2 * r2, P2)</code>
   *
   * @return the pair of values above in an opaque instance
   */
  private static MukuliBatchSemiAggregate prepareBatchVerify2(
      int index,
      List<MikuliPublicKey> publicKeys1,
      Bytes message1,
      MikuliSignature signature1,
      List<MikuliPublicKey> publicKeys2,
      Bytes message2,
      MikuliSignature signature2) {

    G2Point sigG2Point1;
    G2Point msgG2Point1;
    if (index == 0) {
      // optimization: we may omit multiplication of a single component (i.e. multiplier is 1)
      // let it be the component with index 0
      sigG2Point1 = signature1.g2Point();
      msgG2Point1 = G2Point.hashToG2(message1);
    } else {
      Scalar randomMult = nextBatchRandomMultiplier();
      sigG2Point1 = signature1.g2Point().mul(randomMult);
      msgG2Point1 = G2Point.hashToG2(message1).mul(randomMult);
    }
    MikuliPublicKey publicKey1 = MikuliPublicKey.aggregate(publicKeys1);

    Scalar randomMult2 = nextBatchRandomMultiplier();
    G2Point sigG2Point2 = signature2.g2Point().mul(randomMult2);
    G2Point msgG2Point2 = G2Point.hashToG2(message2).mul(randomMult2);
    MikuliPublicKey publicKey2 = MikuliPublicKey.aggregate(publicKeys2);

    GTPoint pair2 =
        AtePairing.pair2NoExp(publicKey1.g1Point(), msgG2Point1, publicKey2.g1Point(), msgG2Point2);

    return new MukuliBatchSemiAggregate(sigG2Point1.add(sigG2Point2), pair2);
  }

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Does the final job of batch verification: calculates the final product and sum, does final
   * pairing and exponentiation
   *
   * @param preparedList the list of instances returned by {@link #prepareBatchVerify(int, List,
   *     Bytes, Signature)} or {@link #prepareBatchVerify2(int, List, Bytes, MikuliSignature, List,
   *     Bytes, MikuliSignature)} or mixed from both
   * @return True if the verification is successful, false otherwise
   */
  @Override
  public boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList) {
    if (preparedList.isEmpty()) {
      return true;
    }
    G2Point sigSum = null;
    GTPoint pairProd = null;
    for (BatchSemiAggregate semiSig : preparedList) {
      MukuliBatchSemiAggregate mSemiSig = (MukuliBatchSemiAggregate) semiSig;
      sigSum = sigSum == null ? mSemiSig.getSigPoint() : sigSum.add(mSemiSig.getSigPoint());
      pairProd =
          pairProd == null
              ? mSemiSig.getMsgPubKeyPairing()
              : pairProd.mul(mSemiSig.getMsgPubKeyPairing());
    }
    GTPoint sigPair = AtePairing.pairNoExp(Util.g1Generator, sigSum);
    return AtePairing.fexp(sigPair).equals(AtePairing.fexp(pairProd));
  }

  private static Scalar nextBatchRandomMultiplier() {
    long randomLong =
        (getRND().nextLong() & 0x7fffffffffffffffL) % MAX_BATCH_VERIFY_RANDOM_MULTIPLIER;
    BIG randomBig = longToBIG(randomLong);
    return new Scalar(randomBig);
  }

  private static BIG longToBIG(long l) {
    long[] bigContent = new long[BIG.NLEN];
    bigContent[0] = l;
    return new BIG(bigContent);
  }
}
