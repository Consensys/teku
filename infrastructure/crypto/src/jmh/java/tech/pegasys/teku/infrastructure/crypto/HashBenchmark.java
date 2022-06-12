/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HashBenchmark {

  private final Bytes data1 = Bytes32.random();
  private final Bytes data2 = Bytes32.random();

  @Benchmark
  @Fork(2)
  public void measureSha256_concatenate(Blackhole blackhole) {
    blackhole.consume(Hash.sha256(Bytes.concatenate(data1, data2)));
  }

  @Benchmark
  @Fork(2)
  public void measureSha256_multiarg(Blackhole blackhole) {
    blackhole.consume(Hash.sha256(data1, data2));
  }
}
