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

import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve.hashToG2;

import java.util.concurrent.TimeUnit;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;
import org.apache.milagro.amcl.RAND;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.impl.mikuli.G1Point;
import tech.pegasys.teku.bls.impl.mikuli.G2Point;
import tech.pegasys.teku.bls.impl.mikuli.GTPoint;
import tech.pegasys.teku.bls.impl.mikuli.MikuliKeyPair;
import tech.pegasys.teku.bls.impl.mikuli.MikuliSignature;
import tech.pegasys.teku.bls.impl.mikuli.Scalar;

@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
public class BLSPrimitivesBenchmark {

  private static final RAND RANDOM = new RAND();
  private static final BIG MAX_BATCH_VERIFY_RANDOM_MULTIPLIER;

  static G1Point g1Generator = new G1Point(ECP.generator());
  static BIG Big64;
  static BIG Big32;

  static {
    BIG b = new BIG(1);
    b.shl(31);
    Big32 = new BIG(b);
    b.shl(32);
    Big64 = new BIG(b);
    MAX_BATCH_VERIFY_RANDOM_MULTIPLIER = b;
  }

  MikuliKeyPair keyPair = MikuliKeyPair.random(123);
  Bytes32 message = Bytes32.random();
  MikuliSignature signature = keyPair.getSecretKey().sign(message);
  FP12 gtPoint = PAIR.ate(signature.g2Point().getPoint(), g1Generator.getPoint());

  @Benchmark
  public void gtProduct(Blackhole bh) {
    GTPoint r = new GTPoint(gtPoint).mul(new GTPoint(gtPoint));
    bh.consume(r);
  }

  @Benchmark
  public void bigRandomX64(Blackhole bh) {
    BIG randomBig = BIG.randomnum(MAX_BATCH_VERIFY_RANDOM_MULTIPLIER, RANDOM);
    bh.consume(randomBig);
  }

  @Benchmark
  public void g2Mul32(Blackhole bh) {
    G2Point r = signature.g2Point().mul(new Scalar(Big32));
    bh.consume(r);
  }

  @Benchmark
  public void g2Mul64(Blackhole bh) {
    G2Point r = signature.g2Point().mul(new Scalar(Big64));
    bh.consume(r);
  }

  @Benchmark
  public void hToG2(Blackhole bh) {
    ECP2 r = hashToG2(message);
    bh.consume(r);
  }

  @Benchmark
  public void fexp(Blackhole bh) {
    FP12 r = PAIR.fexp(gtPoint);
    bh.consume(r);
  }

  @Benchmark
  public void ate1(Blackhole bh) {
    FP12 ate = PAIR.ate(signature.g2Point().getPoint(), g1Generator.getPoint());
    bh.consume(ate);
  }

  @Benchmark
  public void ate1x2Mul(Blackhole bh) {
    FP12 ate1 = PAIR.ate(signature.g2Point().getPoint(), g1Generator.getPoint());
    FP12 ate2 = PAIR.ate(signature.g2Point().getPoint(), g1Generator.getPoint());
    ate1.mul(ate2);
    bh.consume(ate1);
  }

  @Benchmark
  public void ate2(Blackhole bh) {
    FP12 ate =
        PAIR.ate2(
            signature.g2Point().getPoint(),
            g1Generator.getPoint(),
            signature.g2Point().getPoint(),
            g1Generator.getPoint());
    bh.consume(ate);
  }
}
