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

package tech.pegasys.artemis.benchmarks;

import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.bls.mikuli.BLS12381.BatchSemiAggregate;

@Fork(1)
@State(Scope.Thread)
public class BLSBenchmark {

  @Param({"4", "8", "16", "32", "64", "128"})
  int sigCnt = 128;

  List<BLSKeyPair> keyPairs =
      IntStream.range(0, sigCnt).mapToObj(BLSKeyPair::random).collect(Collectors.toList());
  List<Bytes32> messages =
      Stream.generate(Bytes32::random).limit(sigCnt).collect(Collectors.toList());
  List<BLSSignature> signatures =
      Streams.zip(
              keyPairs.stream(),
              messages.stream(),
              (keyPair, msg) -> BLS.sign(keyPair.getSecretKey(), msg))
          .collect(Collectors.toList());

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureSimple() {
    for (int i = 0; i < sigCnt; i++) {
      boolean res = BLS.verify(keyPairs.get(i).getPublicKey(), messages.get(i), signatures.get(i));
      if (!res) throw new IllegalStateException();
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatched() {
    List<BatchSemiAggregate> batchSemiAggregates =
        IntStream.range(0, sigCnt)
            .mapToObj(
                i ->
                    BLS.prepareBatchVerify(
                        Collections.singletonList(keyPairs.get(i).getPublicKey()),
                        messages.get(i),
                        signatures.get(i)))
            .collect(Collectors.toList());
    boolean res = BLS.completeBatchVerify(batchSemiAggregates);
    if (!res) throw new IllegalStateException();
  }
}
