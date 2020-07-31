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

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class BlsTests {

  public static ImmutableMap<String, TestExecutor> BLS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("bls/verify", new BlsVerifyTestExecutor())
          .put("bls/aggregate", new BlsAggregateTestExecutor())
          .put("bls/aggregate_verify", new BlsAggregateVerifyTestExecutor())
          .put("bls/sign", new BlsSignTestExecutor())
          .put("bls/fast_aggregate_verify", new BlsFastAggregateVerifyTestExecutor())
          .build();

  public static BLSSignature parseSignature(final String value) {
    return BLSSignature.fromSSZBytes(Bytes.fromHexStringLenient(value, 96));
  }
}
