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

package tech.pegasys.teku.bls.mikuli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.mikuli.BLS12381.BatchSemiAggregate;

class BLS12381Test {

  private final List<KeyPair> keys =
      IntStream.range(0, 8).mapToObj(KeyPair::random).collect(Collectors.toList());
  private final List<List<PublicKey>> pubKeys =
      keys.stream().map(k -> Collections.singletonList(k.publicKey())).collect(Collectors.toList());
  private final List<Bytes> messages =
      IntStream.range(0, 8)
          .mapToObj(i -> Bytes.wrap(("Hey " + i).getBytes(UTF_8)))
          .collect(Collectors.toList());
  private final List<Signature> signatures =
      Streams.zip(keys.stream(), messages.stream(), (k, m) -> BLS12381.sign(k.secretKey(), m))
          .collect(Collectors.toList());
  private final Bytes aggrSigMsg = messages.get(0);
  private final Signature aggrSig =
      BLS12381.aggregate(keys.stream().limit(3).map(k -> BLS12381.sign(k.secretKey(), aggrSigMsg)));
  private final List<PublicKey> aggrSigPubkeys =
      keys.stream().limit(3).map(KeyPair::publicKey).collect(Collectors.toList());
  private final int aggrIndex;
  private final int invalidIndex;

  public BLS12381Test() {
    aggrIndex = pubKeys.size();
    pubKeys.add(aggrSigPubkeys);
    messages.add(aggrSigMsg);
    signatures.add(aggrSig);

    invalidIndex = pubKeys.size();
    pubKeys.add(pubKeys.get(0));
    messages.add(messages.get(0));
    signatures.add(Signature.random(0));
  }

  @Test
  void signAndVerify() {
    KeyPair keyPair = KeyPair.random(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair.secretKey(), message);
    assertTrue(BLS12381.verify(keyPair.publicKey(), message, signature));
  }

  @Test
  void signAndVerifyDifferentMessage() {
    KeyPair keyPair = KeyPair.random(117);
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair.secretKey(), message1);
    assertFalse(BLS12381.verify(keyPair.publicKey(), message2, signature));
  }

  @Test
  void signAndVerifyDifferentKeys() {
    KeyPair keyPair1 = KeyPair.random(129);
    KeyPair keyPair2 = KeyPair.random(257);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair1.secretKey(), message);
    assertFalse(BLS12381.verify(keyPair2.publicKey(), message, signature));
  }

  @Test
  void fastAggregateVerify() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    KeyPair keyPair1 = KeyPair.random(1);
    KeyPair keyPair2 = KeyPair.random(2);
    KeyPair keyPair3 = KeyPair.random(3);

    List<PublicKey> publicKeys =
        Arrays.asList(keyPair1.publicKey(), keyPair2.publicKey(), keyPair3.publicKey());
    List<Signature> signatures =
        Arrays.asList(
            BLS12381.sign(keyPair1.secretKey(), message),
            BLS12381.sign(keyPair2.secretKey(), message),
            BLS12381.sign(keyPair3.secretKey(), message));
    Signature aggregatedSignature = BLS12381.aggregate(signatures);

    assertTrue(BLS12381.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }

  @Test
  void signingWithZeroSecretKeyGivesPointAtInfinity() {
    SecretKey secretKey = new SecretKey(new Scalar(new BIG(0)));
    Signature sig = BLS12381.sign(secretKey, Bytes.wrap("Hello, world!".getBytes(UTF_8)));
    assertTrue(sig.g2Point().ecp2Point().is_infinity());
  }

  @Test
  void verifyingWithPointsAtInfinityAlwaysSucceeds() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    PublicKey infPubKey = new PublicKey(new G1Point());
    Signature infSignature = new Signature(new G2Point());

    assertTrue(BLS12381.verify(infPubKey, message, infSignature));
  }

  @Test
  void aggregateVerifyDistinctMessages() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    Bytes message3 = Bytes.wrap("Hello, world 3!".getBytes(UTF_8));
    KeyPair keyPair1 = KeyPair.random(1);
    KeyPair keyPair2 = KeyPair.random(2);
    KeyPair keyPair3 = KeyPair.random(3);

    List<PublicKey> publicKeys =
        Arrays.asList(keyPair1.publicKey(), keyPair2.publicKey(), keyPair3.publicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message3);
    List<Signature> signatures =
        Arrays.asList(
            BLS12381.sign(keyPair1.secretKey(), message1),
            BLS12381.sign(keyPair2.secretKey(), message2),
            BLS12381.sign(keyPair3.secretKey(), message3));
    Signature aggregatedSignature = BLS12381.aggregate(signatures);

    assertTrue(BLS12381.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void aggregateVerifyDuplicateMessages() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    KeyPair keyPair1 = KeyPair.random(1);
    KeyPair keyPair2 = KeyPair.random(2);
    KeyPair keyPair3 = KeyPair.random(3);

    List<PublicKey> publicKeys =
        Arrays.asList(keyPair1.publicKey(), keyPair2.publicKey(), keyPair3.publicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message2);
    List<Signature> signatures =
        Arrays.asList(
            BLS12381.sign(keyPair1.secretKey(), message1),
            BLS12381.sign(keyPair2.secretKey(), message2),
            BLS12381.sign(keyPair3.secretKey(), message2));
    Signature aggregatedSignature = BLS12381.aggregate(signatures);

    assertFalse(BLS12381.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void batchVerifyPositiveSimpleTest() {
    // positive simple case
    for (int sigCnt = 0; sigCnt < 6; sigCnt++) {
      List<BatchSemiAggregate> semiAggr =
          IntStream.range(0, sigCnt)
              .mapToObj(
                  i ->
                      BLS12381.prepareBatchVerify(
                          i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(BLS12381.completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerifyPositiveSimpleWithAggrSigTest() {
    // positive simple case with aggr sig
    for (int aggrPos = 0; aggrPos < 4; aggrPos++) {
      int finalAggrPos = aggrPos;
      List<BatchSemiAggregate> semiAggr =
          IntStream.range(0, 4)
              .map(i -> i == finalAggrPos ? aggrIndex : i)
              .mapToObj(
                  i ->
                      BLS12381.prepareBatchVerify(
                          i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(BLS12381.completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerify2PositiveTest() {
    // positive prepareBatchVerify2 test
    for (int sigCnt = 0; sigCnt < 8; sigCnt++) {
      Stream<List<Integer>> pairsStream =
          Lists.partition(IntStream.range(0, sigCnt).boxed().collect(Collectors.toList()), 2)
              .stream();
      List<BatchSemiAggregate> semiAggr =
          pairsStream
              .map(
                  l -> {
                    if (l.size() == 1) {
                      return BLS12381.prepareBatchVerify(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)));
                    } else {
                      return BLS12381.prepareBatchVerify2(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)),
                          pubKeys.get(l.get(1)),
                          messages.get(l.get(1)),
                          signatures.get(l.get(1)));
                    }
                  })
              .collect(Collectors.toList());

      assertTrue(BLS12381.completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerify2NegativeTest() {
    // negative prepareBatchVerify2 test
    for (int sigCnt = 0; sigCnt < 5; sigCnt++) {
      int finalSigCnt = sigCnt;
      Stream<List<Integer>> pairsStream =
          Lists.partition(
              IntStream.range(0, 5)
                  .map(i -> i == finalSigCnt ? invalidIndex : i)
                  .boxed()
                  .collect(Collectors.toList()),
              2)
              .stream();
      List<BatchSemiAggregate> semiAggr =
          pairsStream
              .map(
                  l -> {
                    if (l.size() == 1) {
                      return BLS12381.prepareBatchVerify(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)));
                    } else {
                      return BLS12381.prepareBatchVerify2(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)),
                          pubKeys.get(l.get(1)),
                          messages.get(l.get(1)),
                          signatures.get(l.get(1)));
                    }
                  })
              .collect(Collectors.toList());

      assertFalse(BLS12381.completeBatchVerify(semiAggr));
    }
  }
}
