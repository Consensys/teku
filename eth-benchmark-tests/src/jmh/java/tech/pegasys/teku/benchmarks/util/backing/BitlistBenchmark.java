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

package tech.pegasys.teku.benchmarks.util.backing;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class BitlistBenchmark {

  static SszListSchema<SszBit> type = new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 4096);
  static SszList<SszBit> bitlist;

  static {
    SszMutableList<SszBit> wBitlist = type.getDefault().createWritableCopy();
    for (int i = 0; i < type.getMaxLength(); i++) {
      wBitlist.append(SszBit.viewOf(true));
    }
    bitlist = wBitlist.commitChanges();
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void fromCachedListView(Blackhole bh) {
    bh.consume(SszUtils.getBitlist(bitlist));
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void fromNewListView(Blackhole bh) {
    SszList<SszBit> freshListView = type.createFromBackingNode(bitlist.getBackingNode());
    bh.consume(SszUtils.getBitlist(freshListView));
  }
}
