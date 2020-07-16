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
import tech.pegasys.teku.bls.BatchSemiAggregate;

class BLS12381Test {

  private final List<MikuliKeyPair> keys =
      IntStream.range(0, 8).mapToObj(MikuliKeyPair::random).collect(Collectors.toList());
  private final List<List<MikuliPublicKey>> pubKeys =
      keys.stream()
          .map(k -> Collections.singletonList(k.getPublicKey()))
          .collect(Collectors.toList());
  private final List<Bytes> messages =
      IntStream.range(0, 8)
          .mapToObj(i -> Bytes.wrap(("Hey " + i).getBytes(UTF_8)))
          .collect(Collectors.toList());
  private final List<MikuliSignature> signatures =
      Streams.zip(
              keys.stream(), messages.stream(), (k, m) -> MikuliBLS12381.sign(k.getSecretKey(), m))
          .collect(Collectors.toList());
  private final Bytes aggrSigMsg = messages.get(0);
  private final MikuliSignature aggrSig =
      MikuliBLS12381.aggregate(
          keys.stream().limit(3).map(k -> MikuliBLS12381.sign(k.getSecretKey(), aggrSigMsg)));
  private final List<MikuliPublicKey> aggrSigPubkeys =
      keys.stream().limit(3).map(MikuliKeyPair::getPublicKey).collect(Collectors.toList());
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
    signatures.add(MikuliSignature.random(0));
  }

  @Test
  void signAndVerify() {
    MikuliKeyPair keyPair = MikuliKeyPair.random(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    MikuliSignature signature = MikuliBLS12381.sign(keyPair.getSecretKey(), message);
    assertTrue(MikuliBLS12381.verify(keyPair.getPublicKey(), message, signature));
  }

  @Test
  void signAndVerifyDifferentMessage() {
    MikuliKeyPair keyPair = MikuliKeyPair.random(117);
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    MikuliSignature signature = MikuliBLS12381.sign(keyPair.getSecretKey(), message1);
    assertFalse(MikuliBLS12381.verify(keyPair.getPublicKey(), message2, signature));
  }

  @Test
  void signAndVerifyDifferentKeys() {
    MikuliKeyPair keyPair1 = MikuliKeyPair.random(129);
    MikuliKeyPair keyPair2 = MikuliKeyPair.random(257);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    MikuliSignature signature = MikuliBLS12381.sign(keyPair1.getSecretKey(), message);
    assertFalse(MikuliBLS12381.verify(keyPair2.getPublicKey(), message, signature));
  }

  @Test
  void fastAggregateVerify() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    MikuliKeyPair keyPair1 = MikuliKeyPair.random(1);
    MikuliKeyPair keyPair2 = MikuliKeyPair.random(2);
    MikuliKeyPair keyPair3 = MikuliKeyPair.random(3);

    List<MikuliPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<MikuliSignature> signatures =
        Arrays.asList(
            MikuliBLS12381.sign(keyPair1.getSecretKey(), message),
            MikuliBLS12381.sign(keyPair2.getSecretKey(), message),
            MikuliBLS12381.sign(keyPair3.getSecretKey(), message));
    MikuliSignature aggregatedSignature = MikuliBLS12381.aggregate(signatures);

    assertTrue(MikuliBLS12381.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }

  @Test
  void signingWithZeroSecretKeyGivesPointAtInfinity() {
    MikuliSecretKey secretKey = new MikuliSecretKey(new Scalar(new BIG(0)));
    MikuliSignature sig =
        MikuliBLS12381.sign(secretKey, Bytes.wrap("Hello, world!".getBytes(UTF_8)));
    assertTrue(sig.g2Point().ecp2Point().is_infinity());
  }

  @Test
  void verifyingWithPointsAtInfinityAlwaysSucceeds() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    MikuliPublicKey infPubKey = new MikuliPublicKey(new G1Point());
    MikuliSignature infSignature = new MikuliSignature(new G2Point());

    assertTrue(MikuliBLS12381.verify(infPubKey, message, infSignature));
  }

  @Test
  void aggregateVerifyDistinctMessages() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    Bytes message3 = Bytes.wrap("Hello, world 3!".getBytes(UTF_8));
    MikuliKeyPair keyPair1 = MikuliKeyPair.random(1);
    MikuliKeyPair keyPair2 = MikuliKeyPair.random(2);
    MikuliKeyPair keyPair3 = MikuliKeyPair.random(3);

    List<MikuliPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message3);
    List<MikuliSignature> signatures =
        Arrays.asList(
            MikuliBLS12381.sign(keyPair1.getSecretKey(), message1),
            MikuliBLS12381.sign(keyPair2.getSecretKey(), message2),
            MikuliBLS12381.sign(keyPair3.getSecretKey(), message3));
    MikuliSignature aggregatedSignature = MikuliBLS12381.aggregate(signatures);

    assertTrue(MikuliBLS12381.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void aggregateVerifyDuplicateMessages() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    MikuliKeyPair keyPair1 = MikuliKeyPair.random(1);
    MikuliKeyPair keyPair2 = MikuliKeyPair.random(2);
    MikuliKeyPair keyPair3 = MikuliKeyPair.random(3);

    List<MikuliPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message2);
    List<MikuliSignature> signatures =
        Arrays.asList(
            MikuliBLS12381.sign(keyPair1.getSecretKey(), message1),
            MikuliBLS12381.sign(keyPair2.getSecretKey(), message2),
            MikuliBLS12381.sign(keyPair3.getSecretKey(), message2));
    MikuliSignature aggregatedSignature = MikuliBLS12381.aggregate(signatures);

    assertFalse(MikuliBLS12381.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void batchVerifyPositiveSimpleTest() {
    // positive simple case
    for (int sigCnt = 0; sigCnt < 6; sigCnt++) {
      List<BatchSemiAggregate> semiAggr =
          IntStream.range(0, sigCnt)
              .mapToObj(
                  i ->
                      MikuliBLS12381.INSTANCE.prepareBatchVerify(
                          i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(MikuliBLS12381.INSTANCE.completeBatchVerify(semiAggr));
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
                      MikuliBLS12381.INSTANCE.prepareBatchVerify(
                          i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(MikuliBLS12381.INSTANCE.completeBatchVerify(semiAggr));
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
                      return MikuliBLS12381.INSTANCE.prepareBatchVerify(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)));
                    } else {
                      return MikuliBLS12381.prepareBatchVerify2(
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

      assertTrue(MikuliBLS12381.INSTANCE.completeBatchVerify(semiAggr));
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
                      return MikuliBLS12381.INSTANCE.prepareBatchVerify(
                          l.get(0),
                          pubKeys.get(l.get(0)),
                          messages.get(l.get(0)),
                          signatures.get(l.get(0)));
                    } else {
                      return MikuliBLS12381.prepareBatchVerify2(
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

      assertFalse(MikuliBLS12381.INSTANCE.completeBatchVerify(semiAggr));
    }
  }
}
