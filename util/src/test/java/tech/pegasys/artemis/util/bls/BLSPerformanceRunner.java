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

package tech.pegasys.artemis.util.bls;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.util.alogger.ALogger;

// This test is disabled by default so that it doesn't slow down other tests
@Disabled
public class BLSPerformanceRunner {

  private ALogger LOG = new ALogger(BLSPerformanceRunner.class.getName());

  public BLSPerformanceRunner() {
    Configurator.setRootLevel(Level.INFO);
  }

  private Long executeRun(Runnable r, Integer count) {
    long start = System.currentTimeMillis();
    for (int j = 0; j < count; j++) {
      Thread t = new Thread(r);
      t.start();
    }
    long end = System.currentTimeMillis();
    return end - start;
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCountOrder4")
  void benchmarkVerifyAggregate128(Integer i) {
    Bytes message1 = Bytes.wrap("Message One".getBytes(UTF_8));

    ArrayList<BLSKeyPair> membs = new ArrayList<>();
    for (int j = 0; j < 128; j++) {
      BLSKeyPair keyPair1 = BLSKeyPair.random();
      membs.add(keyPair1);
    }

    Long time =
        executeRun(
            () -> {
              try {
                List<BLSSignature> sigs =
                    membs.stream()
                        .map(
                            pk -> {
                              return BLSSignature.sign(pk, message1, Bytes.wrap(new byte[4]));
                            })
                        .collect(Collectors.toList());

                BLSPublicKey aggKey =
                    BLSPublicKey.aggregate(
                        membs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList()));

                BLSSignature aggSig = BLSSignature.aggregate(sigs);

                List<Bytes> bytes = Collections.nCopies(membs.size(), message1);
                // Verify the aggregate signatures and keys
                aggSig.checkSignature(Arrays.asList(aggKey), bytes, Bytes.wrap(new byte[4]));
              } catch (BLSException e) {
                LOG.log(Level.ERROR, "Exception" + e.toString());
              }
            },
            i);
    LOG.log(Level.INFO, "Time for 128:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCountOrder4")
  void generateRandomSignature(Integer i) {
    Long time = executeRun(BLSSignature::random, i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testAggregateManySignatures(Integer i) {
    final BLSSignature signature = BLSSignature.random();
    final List<BLSSignature> sigs = Collections.nCopies(i, signature);

    Long time =
        executeRun(
            () -> {
              try {
                BLSSignature.aggregate(sigs);
              } catch (BLSException e) {
                LOG.log(Level.ERROR, "Exception" + e.toString());
              }
            },
            1);

    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCountOrder4")
  void testSigning(Integer i) {
    Bytes message1 = Bytes.wrap("Message One".getBytes(UTF_8));

    // 1 & 2 sign message1; 3 & 4 sign message2
    BLSKeyPair keyPair1 = BLSKeyPair.random();

    Long time = executeRun(() -> BLSSignature.sign(keyPair1, message1, Bytes.wrap(new byte[4])), i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCountOrder4")
  void testSigsAndMessagesCheckSignature(Integer i) throws BLSException {
    Bytes message1 = Bytes.wrap("Message One".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Message Two".getBytes(UTF_8));

    // 1 & 2 sign message1; 3 & 4 sign message2
    BLSKeyPair keyPair1 = BLSKeyPair.random();
    BLSKeyPair keyPair2 = BLSKeyPair.random();
    BLSSignature signature1 = BLSSignature.sign(keyPair1, message1, Bytes.wrap(new byte[4]));
    BLSSignature signature2 = BLSSignature.sign(keyPair2, message1, Bytes.wrap(new byte[4]));

    BLSKeyPair keyPair3 = BLSKeyPair.random();
    BLSKeyPair keyPair4 = BLSKeyPair.random();
    BLSSignature signature3 = BLSSignature.sign(keyPair3, message2, Bytes.wrap(new byte[4]));
    BLSSignature signature4 = BLSSignature.sign(keyPair4, message2, Bytes.wrap(new byte[4]));

    // Aggregate keys 1 & 2, and keys 3 & 4
    BLSPublicKey aggregatePublicKey12 =
        BLSPublicKey.aggregate(Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey()));

    BLSPublicKey aggregatePublicKey34 =
        BLSPublicKey.aggregate(Arrays.asList(keyPair3.getPublicKey(), keyPair4.getPublicKey()));

    // Aggregate the signatures
    BLSSignature aggregateSignature =
        BLSSignature.aggregate(Arrays.asList(signature1, signature2, signature3, signature4));

    Long time =
        executeRun(
            () -> {
              try {
                // Verify the aggregate signatures and keys
                aggregateSignature.checkSignature(
                    Arrays.asList(aggregatePublicKey12, aggregatePublicKey34),
                    Arrays.asList(message1, message2),
                    Bytes.wrap(new byte[4]));
              } catch (BLSException e) {
                LOG.log(Level.ERROR, "Exception" + e.toString());
              }
            },
            i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testAggregationTime(Integer i) {
    BLSSignature signature = BLSSignature.random();

    Long time =
        executeRun(
            () -> {
              try {
                BLSSignature.aggregate(Collections.singletonList(signature));
              } catch (BLSException e) {
                LOG.log(Level.ERROR, "Exception" + e.toString());
              }
            },
            i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testBLSPubKeyDeserialize(Integer i) {
    Bytes emptyBytesSsz = SSZ.encode(writer -> writer.writeFixedBytes(Bytes.wrap(new byte[48])));

    Long time = executeRun(() -> BLSPublicKey.fromBytes(emptyBytesSsz), i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testBLSPubKeySerialize(Integer i) {
    BLSPublicKey emptyPublicKey = BLSPublicKey.empty();

    Long time = executeRun(() -> emptyPublicKey.toBytes().toHexString(), i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testSignatureSerialize(Integer i) {
    BLSSignature signature1 = BLSSignature.random();

    Long time = executeRun(signature1::toBytes, i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  @ParameterizedTest()
  @MethodSource("singleAggregationCount")
  void testSignatureDeserialize(Integer i) {
    BLSSignature signature1 = BLSSignature.random();
    Bytes bytes = signature1.toBytes();

    Long time = executeRun(() -> BLSSignature.fromBytes(bytes), i);
    LOG.log(Level.INFO, "Time for i:" + i + ", time:" + time);
  }

  public static Stream<Arguments> singleAggregationCount() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(10));
    args.add(Arguments.of(100));
    args.add(Arguments.of(1000));
    args.add(Arguments.of(10000));
    //    args.add(Arguments.of(100000));
    //    args.add(Arguments.of(1000000));
    return args.stream();
  }

  public static Stream<Arguments> singleAggregationCountOrder4() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(10));
    args.add(Arguments.of(100));
    args.add(Arguments.of(1000));
    //    args.add(Arguments.of(2000));
    return args.stream();
  }
}
