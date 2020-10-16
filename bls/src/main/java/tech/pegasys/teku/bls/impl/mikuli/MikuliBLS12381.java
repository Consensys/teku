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

import java.util.List;
import java.util.Random;
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
    return MikuliSecretKey.fromBytes(secretKeyBytes);
  }

  @Override
  public PublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys) {
    return MikuliPublicKey.aggregate(publicKeys);
  }

  @Override
  public MikuliSignature aggregateSignatures(List<? extends Signature> signatures) {
    return MikuliSignature.aggregate(
        signatures.stream().map(MikuliSignature::fromSignature).collect(Collectors.toList()));
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

    MikuliSignature mikuliSignature = MikuliSignature.fromSignature(signature);
    if (publicKeys.stream().anyMatch(k -> MikuliPublicKey.fromPublicKey(k).isInfinity())) {
      return new MikuliInvalidBatchSemiAggregate();
    }

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
        AtePairing.pairNoExp(MikuliPublicKey.aggregate(publicKeys).g1Point(), msgG2Point);

    return new MukuliBatchSemiAggregate(sigG2Point, pair);
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
  @Override
  public BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<? extends PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<? extends PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2) {

    if (Stream.concat(publicKeys1.stream(), publicKeys2.stream())
        .anyMatch(k -> MikuliPublicKey.fromPublicKey(k).isInfinity())) {
      return new MikuliInvalidBatchSemiAggregate();
    }

    G2Point sigG2Point1;
    G2Point msgG2Point1;
    if (index == 0) {
      // optimization: we may omit multiplication of a single component (i.e. multiplier is 1)
      // let it be the component with index 0
      sigG2Point1 = MikuliSignature.fromSignature(signature1).g2Point();
      msgG2Point1 = G2Point.hashToG2(message1);
    } else {
      Scalar randomMult = nextBatchRandomMultiplier();
      sigG2Point1 = MikuliSignature.fromSignature(signature1).g2Point().mul(randomMult);
      msgG2Point1 = G2Point.hashToG2(message1).mul(randomMult);
    }
    MikuliPublicKey publicKey1 = MikuliPublicKey.aggregate(publicKeys1);

    Scalar randomMult2 = nextBatchRandomMultiplier();
    G2Point sigG2Point2 = MikuliSignature.fromSignature(signature2).g2Point().mul(randomMult2);
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
   *     Bytes, Signature)} or {@link #prepareBatchVerify2(int, List, Bytes, Signature, List, Bytes,
   *     Signature)} or mixed from both
   * @return True if the verification is successful, false otherwise
   */
  @Override
  public boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList) {
    if (preparedList.isEmpty()) {
      return true;
    }
    if (preparedList.stream().anyMatch(it -> it instanceof MikuliInvalidBatchSemiAggregate)) {
      return false;
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

  protected static class MikuliInvalidBatchSemiAggregate implements BatchSemiAggregate {}
}
