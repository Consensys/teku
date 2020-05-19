/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.bls;

import static tech.pegasys.teku.ethtests.finder.BlsTestFinder.BLS_DATA_FILE;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingConsumer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.ExecutableFactory;

public class BlsTestExecutableFactory implements ExecutableFactory {

  public static ImmutableMap<String, ExecutableFactory> BLS_TEST_TYPES =
      ImmutableMap.<String, ExecutableFactory>builder()
          .put("bls/verify", new BlsTestExecutableFactory(new BlsVerifyTestType()))
          .put("bls/aggregate", new BlsTestExecutableFactory(new BlsAggregateTestType()))
          .put(
              "bls/aggregate_verify",
              new BlsTestExecutableFactory(new BlsAggregateVerifyTestType()))
          .put("bls/sign", new BlsTestExecutableFactory(new BlsSignTestType()))
          .put(
              "bls/fast_aggregate_verify",
              new BlsTestExecutableFactory(new BlsFastAggregateVerifyTestType()))
          .build();

  private final ThrowingConsumer<BlsTestData> testMethod;

  public BlsTestExecutableFactory(final ThrowingConsumer<BlsTestData> testMethod) {
    this.testMethod = testMethod;
  }

  @Override
  public Executable forTestDefinition(final TestDefinition testDefinition) {
    return () -> {
      final BlsTestData data =
          BlsTestData.parse(testDefinition.getTestDirectory().resolve(BLS_DATA_FILE));
      testMethod.accept(data);
    };
  }
}
