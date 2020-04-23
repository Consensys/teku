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

package tech.pegasys.artemis.bls.mikuli;

import static tech.pegasys.artemis.bls.hashToG2.HashToCurve.hashToG2;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;

/*
 * (Heavily) adapted from the ConsenSys/mikuli (Apache 2 License) implementation:
 * https://github.com/ConsenSys/mikuli/blob/master/src/main/java/net/consensys/mikuli/crypto/*.java
 */

/**
 * This Boneh-Lynn-Shacham (BLS) signature implementation is constructed from a pairing friendly
 * BLS12-381 elliptic curve. It implements a subset of the functions from the proposed IETF
 * standard.
 *
 * <p>Reference: https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00
 *
 * <p>This class depends upon the Apache Milagro library being available. See
 * https://milagro.apache.org.
 *
 * <p>Apache Milagro can be included using the gradle dependency
 * 'org.miracl.milagro.amcl:milagro-crypto-java'.
 */
public final class BLS12381 {

  private static final long MAX_BATCH_VERIFY_RANDOM_MULTIPLIER = Long.MAX_VALUE;
  private static SecureRandom RND;

  private static SecureRandom getRND() {
    // Milagro RAND has some issues with generating 'small' random numbers
    // and is not thread safe
    if (RND == null) {
      try {
        RND = SecureRandom.getInstanceStrong();
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
    return RND;
  }

  /**
   * Opaque data class which contains intermediate calculation results for batch BLS verification
   *
   * @see #prepareBatchVerify(int, List, Bytes, Signature)
   * @see #prepareBatchVerify2(int, List, Bytes, Signature, List, Bytes, Signature)
   * @see #completeBatchVerify(List)
   */
  public static final class BatchSemiAggregate {
    private final G2Point sigPoint;
    private final GTPoint msgPubKeyPairing;

    private BatchSemiAggregate(G2Point sigPoint, GTPoint msgPubKeyPairing) {
      this.sigPoint = sigPoint;
      this.msgPubKeyPairing = msgPubKeyPairing;
    }

    private G2Point getSigPoint() {
      return sigPoint;
    }

    private GTPoint getMsgPubKeyPairing() {
      return msgPubKeyPairing;
    }
  }

  /*
   * Methods used directly in the Ethereum 2.0 specifications.
   */

  /**
   * Generates a Signature from a private key and message.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.2.1
   *
   * @param secretKey The secret key, not null
   * @param message The message to sign, not null
   * @return The Signature, not null
   */
  public static Signature sign(SecretKey secretKey, Bytes message) {
    G2Point hashInGroup2 = new G2Point(hashToG2(message));
    return new Signature(secretKey.sign(hashInGroup2));
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * <p>Implements the basic scheme of
   * https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.1 which is identical to
   * CoreVerify().
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @return True if the verification is successful, false otherwise.
   */
  public static boolean verify(PublicKey publicKey, Bytes message, Signature signature) {
    return coreVerify(publicKey, message, signature);
  }

  /**
   * Aggregates a list of signatures into a single signature using BLS magic.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.7
   *
   * @param signatures the list of signatures to be aggregated
   * @return the aggregated signature
   */
  public static Signature aggregate(List<Signature> signatures) {
    return new Signature(Signature.aggregate(signatures));
  }

  /**
   * Aggregates a stream of signatures into a single signature using BLS magic.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.7
   *
   * @param signatures the stream of signatures to be aggregated
   * @return the aggregated signature
   */
  public static Signature aggregate(Stream<Signature> signatures) {
    return new Signature(Signature.aggregate(signatures));
  }

  /**
   * Verifies an aggregate signature against a list of distinct messages using the list of public
   * keys.
   *
   * <p>https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.1.1
   *
   * @param publicKeys The list of public keys, not null
   * @param messages The list of messages to verify, all distinct, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean aggregateVerify(
      List<PublicKey> publicKeys, List<Bytes> messages, Signature signature) {
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
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.3.4
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean fastAggregateVerify(
      List<PublicKey> publicKeys, Bytes message, Signature signature) {
    return verify(PublicKey.aggregate(publicKeys), message, signature);
  }

  /*
   * Other methods defined by the standard and used above.
   */

  /**
   * The CoreVerify algorithm checks that a signature is valid for the octet string message under
   * the public key publicKey.
   *
   * <p>https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.6
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  private static boolean coreVerify(PublicKey publicKey, Bytes message, Signature signature) {
    G2Point hashInGroup2 = new G2Point(hashToG2(message));
    return signature.verify(publicKey, hashInGroup2);
  }

  /**
   * Verifies an aggregate signature against a list of distinct messages using the list of public
   * keys.
   *
   * <p>https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.8
   *
   * @param publicKeys The list of public keys, not null
   * @param messages The list of messages to verify, all distinct, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean coreAggregateVerify(
      List<PublicKey> publicKeys, List<Bytes> messages, Signature signature) {
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
  public static BatchSemiAggregate prepareBatchVerify(
      int index, List<PublicKey> publicKeys, Bytes message, Signature signature) {

    G2Point sigG2Point;
    G2Point msgG2Point;
    if (index == 0) {
      // optimization: we may omit multiplication of a single component (i.e. multiplier is 1)
      // let it be the component with index 0
      sigG2Point = signature.g2Point();
      msgG2Point = G2Point.hashToG2(message);
    } else {
      Scalar randomMult = nextBatchRandomMultiplier();
      sigG2Point = signature.g2Point().mul(randomMult);
      msgG2Point = G2Point.hashToG2(message).mul(randomMult);
    }

    GTPoint pair = AtePairing.pairNoExp(PublicKey.aggregate(publicKeys).g1Point(), msgG2Point);

    return new BatchSemiAggregate(sigG2Point, pair);
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
  public static BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2) {

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
    PublicKey publicKey1 = PublicKey.aggregate(publicKeys1);

    Scalar randomMult2 = nextBatchRandomMultiplier();
    G2Point sigG2Point2 = signature2.g2Point().mul(randomMult2);
    G2Point msgG2Point2 = G2Point.hashToG2(message2).mul(randomMult2);
    PublicKey publicKey2 = PublicKey.aggregate(publicKeys2);

    GTPoint pair2 =
        AtePairing.pair2NoExp(publicKey1.g1Point(), msgG2Point1, publicKey2.g1Point(), msgG2Point2);

    return new BatchSemiAggregate(sigG2Point1.add(sigG2Point2), pair2);
  }

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Does the final job of batch verification: calculates the final product and sum, does final
   * pairing and exponentiation
   *
   * @param preparedList the list of instances returned by {@link #prepareBatchVerify(int, List,
   *     Bytes, Signature)} or {@link #prepareBatchVerify2(int, List, Bytes, Signature, List, Bytes,
   *     Signature)} or mixed from both
   * @return True if the verification is successful, false otherwise
   */
  public static boolean completeBatchVerify(List<BatchSemiAggregate> preparedList) {
    if (preparedList.isEmpty()) {
      return true;
    }
    G2Point sigSum = null;
    GTPoint pairProd = null;
    for (BatchSemiAggregate semiSig : preparedList) {
      // TODO can be optimized here to perform log2(N) mul/add operations
      sigSum = sigSum == null ? semiSig.getSigPoint() : sigSum.add(semiSig.getSigPoint());
      pairProd =
          pairProd == null
              ? semiSig.getMsgPubKeyPairing()
              : pairProd.mul(semiSig.getMsgPubKeyPairing());
    }
    GTPoint sigPair = AtePairing.pairNoExp(KeyPair.g1Generator, sigSum);
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
