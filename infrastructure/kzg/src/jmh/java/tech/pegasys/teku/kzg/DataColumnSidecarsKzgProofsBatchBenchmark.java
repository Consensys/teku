/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.kzg;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@State(Scope.Thread)
public class DataColumnSidecarsKzgProofsBatchBenchmark {

  final SidecarBenchmarkConfig config = new SidecarBenchmarkConfig(true, true);

  @Benchmark
  @Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10)
  public void test() {
    config.miscHelpersFulu.verifyDataColumnSidecarKzgProofsBatch(
        config.kzg, config.dataColumnSidecars);
  }
}
