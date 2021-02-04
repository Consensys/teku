/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.blst;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.KeyPair;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.P2;
import tech.pegasys.teku.bls.impl.blst.swig.P2_Affine;
import tech.pegasys.teku.bls.impl.blst.swig.Pairing;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static tech.pegasys.teku.bls.impl.blst.HashToCurve.ETH2_DST;

public class BlstBLS12381 implements BLS12381 {
  private static final Logger LOG = LogManager.getLogger();

  public static final Optional<BlstBLS12381> INSTANCE;

  private static final int BATCH_RANDOM_BYTES = 8;

  static {
    boolean libraryLoaded;
    try {
      JBlst.loadNativeLibrary();
      libraryLoaded = true;
      LOG.debug("Successfully loaded native BLS library");
    } catch (UnsupportedOperationException e) {
      LOG.debug("Couldn't load native BLS library: " + e);
      libraryLoaded = false;
    }
    INSTANCE = libraryLoaded ? Optional.of(new BlstBLS12381()) : Optional.empty();
  }

  private static Random getRND() {
    // Milagro RAND has some issues with generating 'small' random numbers
    // and is not thread safe
    // Using non-secure random due to the JDK Linux secure random issue:
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6521844
    // A potential attack here has a very limited application and is not feasible
    // Thus using non-secure random doesn't significantly mitigate the security
    return ThreadLocalRandom.current();
  }

  public static BlstSignature sign(BlstSecretKey secretKey, Bytes message) {
    return sign(secretKey, message, HashToCurve.ETH2_DST);
  }

  public static BlstSignature sign(BlstSecretKey secretKey, Bytes message, String dst) {
    if (secretKey.isZero()) {
      throw new IllegalArgumentException("Signing with zero private key is prohibited");
    }

    P2 sig = new P2();
    byte[] sig_for_wire =
        sig.hash_to(message.toArrayUnsafe(), dst, new byte[0])
            .sign_with(secretKey.getKey())
            .serialize();

    return new BlstSignature(new P2_Affine(sig_for_wire), true);
  }

  public static boolean verify(BlstPublicKey publicKey, Bytes message, BlstSignature signature) {
    return verify(publicKey, message, signature, HashToCurve.ETH2_DST);
  }

  public static boolean verify(
      BlstPublicKey publicKey, Bytes message, BlstSignature signature, String dst) {
    if (publicKey.isInfinity()) {
      return false;
    }

    if (publicKey.isInfinity() || signature.isInfinity()) {
      return publicKey.isInfinity() && signature.isInfinity();
    }

    BLST_ERROR res =
        signature.ec2Point.core_verify(publicKey.ecPoint, true, message.toArrayUnsafe(), dst);
    return res == BLST_ERROR.BLST_SUCCESS;
  }

  @Override
  public KeyPair generateKeyPair(Random random) {
    BlstSecretKey secretKey = BlstSecretKey.generateNew(random);
    return new KeyPair(secretKey);
  }

  @Override
  public BlstPublicKey publicKeyFromCompressed(Bytes48 compressedPublicKeyBytes) {
    return BlstPublicKey.fromBytes(compressedPublicKeyBytes);
  }

  @Override
  public BlstSignature signatureFromCompressed(Bytes compressedSignatureBytes) {
    return BlstSignature.fromBytes(compressedSignatureBytes);
  }

  @Override
  public BlstSecretKey secretKeyFromBytes(Bytes32 secretKeyBytes) {
    return BlstSecretKey.fromBytes(secretKeyBytes);
  }

  @Override
  public BlstPublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys) {
    return BlstPublicKey.aggregate(
        publicKeys.stream().map(BlstPublicKey::fromPublicKey).collect(Collectors.toList()));
  }

  @Override
  public BlstSignature aggregateSignatures(List<? extends Signature> signatures) {
    return BlstSignature.aggregate(
        signatures.stream().map(BlstSignature::fromSignature).collect(Collectors.toList()));
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature) {

    BlstPublicKey aggrPubKey = aggregatePublicKeys(publicKeys);
    BlstSignature blstSignature = BlstSignature.fromSignature(signature);
    if (aggrPubKey.isInfinity()) {
      return new BlstInfiniteSemiAggregate(false);
    }

    if (aggrPubKey.isInfinity() || blstSignature.isInfinity()) {
      return new BlstInfiniteSemiAggregate(aggrPubKey.isInfinity() && blstSignature.isInfinity());
    }
    return blstPrepareBatchVerify(aggrPubKey, message, blstSignature);
  }

  BatchSemiAggregate blstPrepareBatchVerify(
      BlstPublicKey pubKey, Bytes message, BlstSignature blstSignature) {

    P2 g2Hash = HashToCurve.hashToG2(message);
    Pairing ctx = new Pairing(true, ETH2_DST);
    try {
      BLST_ERROR ret =
          ctx.mul_n_aggregate(
              pubKey.ecPoint,
              blstSignature.ec2Point,
              nextBatchRandomMultiplier(),
              message.toArray());
      if (ret != BLST_ERROR.BLST_SUCCESS) {
        throw new IllegalArgumentException("Error: " + ret);
      }

      ctx.commit();

      return new BlstFiniteSemiAggregate(ctx);
    } catch (Exception e) {
      ctx.delete();
      throw e;
    } finally {
      g2Hash.delete();
    }
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
    BatchSemiAggregate aggregate1 = prepareBatchVerify(index, publicKeys1, message1, signature1);
    BatchSemiAggregate aggregate2 =
        prepareBatchVerify(index + 1, publicKeys2, message2, signature2);

    return BlstFiniteSemiAggregate.merge(aggregate1, aggregate2);
  }

  @Override
  public boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList) {
    try {
      boolean anyInvalidInfinity =
          preparedList.stream()
              .filter(a -> a instanceof BlstInfiniteSemiAggregate)
              .map(a -> (BlstInfiniteSemiAggregate) a)
              .anyMatch(a -> !a.isValid());

      List<BlstFiniteSemiAggregate> blstList =
          preparedList.stream()
              .filter(a -> a instanceof BlstFiniteSemiAggregate)
              .map(b -> (BlstFiniteSemiAggregate) b)
              .collect(Collectors.toList());

      if (blstList.isEmpty()) {
        return !anyInvalidInfinity;
      }
      Pairing ctx0 = blstList.get(0).getCtx();
      boolean mergeRes = true;
      for (int i = 1; i < blstList.size(); i++) {
        BLST_ERROR ret = ctx0.merge(blstList.get(i).getCtx());
        mergeRes &= ret == BLST_ERROR.BLST_SUCCESS;
      }

      return mergeRes && ctx0.finalverify() && !anyInvalidInfinity;

    } finally {
      preparedList.stream()
          .filter(a -> a instanceof BlstFiniteSemiAggregate)
          .map(b -> (BlstFiniteSemiAggregate) b)
          .forEach(BlstFiniteSemiAggregate::release);
    }
  }

  static BigInteger nextBatchRandomMultiplier() {
    byte[] scalarBytes = new byte[BATCH_RANDOM_BYTES];
    getRND().nextBytes(scalarBytes);
    return new BigInteger(1, scalarBytes);
  }
}
