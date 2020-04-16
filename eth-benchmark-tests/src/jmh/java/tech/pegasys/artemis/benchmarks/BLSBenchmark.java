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

import static tech.pegasys.artemis.bls.hashToG2.HashToCurve.hashToG2;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.bls.mikuli.G1Point;
import tech.pegasys.artemis.bls.mikuli.G2Point;
import tech.pegasys.artemis.bls.mikuli.Scalar;

@Fork(1)
//@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Thread)
public class BLSBenchmark {

  @Param({/*"4", "8", */"16", "32"/*, "64", "128"*/})
  int sigCnt = 128;
  static final G1Point g1Generator = new G1Point(ECP.generator());

  List<BLSKeyPair> keyPairs = IntStream.range(0, sigCnt).mapToObj(BLSKeyPair::random)
      .collect(Collectors.toList());
  List<Bytes32> messages =
      Stream.generate(Bytes32::random).limit(sigCnt).collect(Collectors.toList());
  List<BLSSignature> signatures = Streams.zip(
      keyPairs.stream(),
      messages.stream(),
      (keyPair, msg) -> BLS.sign(keyPair.getSecretKey(), msg))
      .collect(Collectors.toList());

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureSimple(Blackhole bh) {
    for (int i = 0; i < sigCnt; i++) {
      boolean res = BLS
          .verify(keyPairs.get(i).getPublicKey(), messages.get(i), signatures.get(i));
      if (!res) throw new IllegalStateException();
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatched(Blackhole bh) {
    G2Point sigSum = null;
    BIG randomBig = new BIG(0x23456789);
    Scalar random = new Scalar(randomBig);

    for (int i = 0; i < sigCnt; i++) {
      G2Point sigPoint = signatures.get(i).getSignature().g2Point();
      sigPoint = sigPoint.mul(random);
      sigSum = sigSum == null ? sigPoint : sigSum.add(sigPoint);
    }

    FP12 atesProduct = null;
    for (int i = 0; i < sigCnt; i++) {
      ECP pubKeyPoint = keyPairs.get(i).getPublicKey().getPublicKey().g1Point().point;
      ECP2 msgPoint = hashToG2(messages.get(i));
      msgPoint = msgPoint.mul(randomBig);
      FP12 ate = PAIR.ate(msgPoint, pubKeyPoint);
      if (atesProduct == null) {
        atesProduct = ate;
      } else {
        atesProduct.mul(ate);
      }
    }

    FP12 ateSig = PAIR.ate(sigSum.point, g1Generator.point);
    boolean res = PAIR.fexp(ateSig).equals(PAIR.fexp(atesProduct));
    if (!res) throw new IllegalStateException();
  }
}
