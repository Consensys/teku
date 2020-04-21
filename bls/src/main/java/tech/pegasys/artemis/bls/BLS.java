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

package tech.pegasys.artemis.bls;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.mikuli.BLS12381;
import tech.pegasys.artemis.bls.mikuli.BLS12381.BatchSemiAggregate;
import tech.pegasys.artemis.bls.mikuli.PublicKey;

/**
 * Implements the standard interfaces for BLS methods as defined in
 * https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00
 */
public class BLS {

  /**
   * Generates a BLSSignature from a private key and message.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.2.1
   *
   * @param secretKey The secret key, not null
   * @param message The message to sign, not null
   * @return The Signature, not null
   */
  public static BLSSignature sign(BLSSecretKey secretKey, Bytes message) {
    return new BLSSignature(BLS12381.sign(secretKey.getSecretKey(), message));
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.2.2
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @return True if the verification is successful, false otherwise.
   */
  public static boolean verify(BLSPublicKey publicKey, Bytes message, BLSSignature signature) {
    return BLS12381.verify(publicKey.getPublicKey(), message, signature.getSignature());
  }

  /**
   * Aggregates a list of BLSSignatures into a single BLSSignature.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.7
   *
   * @param signatures the list of signatures to be aggregated
   * @return the aggregated signature
   */
  public static BLSSignature aggregate(List<BLSSignature> signatures) {
    return aggregate(signatures.stream());
  }

  /**
   * Aggregates a stream of BLSSignatures into a single BLSSignature.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.7
   *
   * @param signatures the stream of signatures to be aggregated
   * @return the aggregated signature
   */
  public static BLSSignature aggregate(final Stream<BLSSignature> signatures) {
    return new BLSSignature(BLS12381.aggregate(signatures.map(BLSSignature::getSignature)));
  }

  /**
   * Verifies an aggregate BLS signature against a list of distinct messages using the list of
   * public keys.
   *
   * <p>https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.1.1
   *
   * @param publicKeys The list of public keys, not null
   * @param messages The list of messages to verify, all distinct, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean aggregateVerify(
      List<BLSPublicKey> publicKeys, List<Bytes> messages, BLSSignature signature) {
    List<PublicKey> publicKeyObjects =
        publicKeys.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList());
    return BLS12381.aggregateVerify(publicKeyObjects, messages, signature.getSignature());
  }

  /**
   * Verifies an aggregate BLS signature against a message using the list of public keys.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.3.4
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean fastAggregateVerify(
      List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {
    List<PublicKey> publicKeyObjects =
        publicKeys.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList());
    return BLS12381.fastAggregateVerify(publicKeyObjects, message, signature.getSignature());
  }

  public static boolean batchVerify(
      List<List<BLSPublicKey>> publicKeys, List<Bytes> messages, List<BLSSignature> signatures) {
    Preconditions.checkArgument(
        publicKeys.size() == messages.size() && publicKeys.size() == signatures.size(),
        "Different collection sizes");

    int count = publicKeys.size();
    if (count == 0) {
      return true;
    } else if (count == 1) {
      return fastAggregateVerify(publicKeys.get(0), messages.get(0), signatures.get(0));
    } else {
      // double pairing variant is normally slightly faster, but when the number of
      // signatures is relatively small the parallelization of hashToG2 internally
      // yields more performance gain than double pairing
      boolean doublePairing = count > Runtime.getRuntime().availableProcessors() * 2;
      return batchVerify(publicKeys, messages, signatures, doublePairing, true);
    }
  }

  public static boolean batchVerify(
      List<List<BLSPublicKey>> publicKeys,
      List<Bytes> messages,
      List<BLSSignature> signatures,
      boolean doublePairing,
      boolean parallel) {
    Preconditions.checkArgument(
        publicKeys.size() == messages.size() && publicKeys.size() == signatures.size(),
        "Different collection sizes");
    int count = publicKeys.size();
    if (doublePairing) {
      Stream<List<Integer>> pairsStream =
          Lists.partition(IntStream.range(0, count).boxed().collect(Collectors.toList()), 2)
              .stream();

      if (parallel) {
        pairsStream = pairsStream.parallel();
      }
      return completeBatchVerify(
          pairsStream
              .map(
                  idx ->
                      idx.size() == 1
                          ? prepareBatchVerify(
                              idx.get(0),
                              publicKeys.get(idx.get(0)),
                              messages.get(idx.get(0)),
                              signatures.get(idx.get(0)))
                          : prepareBatchVerify2(
                              idx.get(0),
                              publicKeys.get(idx.get(0)),
                              messages.get(idx.get(0)),
                              signatures.get(idx.get(0)),
                              publicKeys.get(idx.get(1)),
                              messages.get(idx.get(1)),
                              signatures.get(idx.get(1))))
              .collect(Collectors.toList()));
    } else {
      Stream<Integer> indexStream = IntStream.range(0, count).boxed();

      if (parallel) {
        indexStream = indexStream.parallel();
      }
      return completeBatchVerify(
          indexStream
              .map(
                  idx ->
                      prepareBatchVerify(
                          idx, publicKeys.get(idx), messages.get(idx), signatures.get(idx)))
              .collect(Collectors.toList()));
    }
  }

  public static BatchSemiAggregate prepareBatchVerify(
      int index, List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {
    return BLS12381.prepareBatchVerify(
        index,
        publicKeys.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList()),
        message,
        signature.getSignature());
  }

  public static BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<BLSPublicKey> publicKeys1,
      Bytes message1,
      BLSSignature signature1,
      List<BLSPublicKey> publicKeys2,
      Bytes message2,
      BLSSignature signature2) {
    return BLS12381.prepareBatchVerify2(
        index,
        publicKeys1.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList()),
        message1,
        signature1.getSignature(),
        publicKeys2.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList()),
        message2,
        signature2.getSignature());
  }

  public static boolean completeBatchVerify(List<BatchSemiAggregate> preparedSignatures) {
    return BLS12381.completeBatchVerify(preparedSignatures);
  }
}
