/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;

public class SszGenericTests {

  public static ImmutableMap<String, TestExecutor> SSZ_GENERIC_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Generic
          .put("ssz_generic/basic_vector", new SszGenericBasicVectorTestExecutor())
          .put("ssz_generic/bitlist", new SszGenericBitlistTestExecutor())
          .put("ssz_generic/bitvector", new SszGenericBitvectorTestExecutor())
          .put("ssz_generic/boolean", new SszGenericBooleanTestExecutor())
          .put("ssz_generic/containers", new SszGenericContainerTestExecutor())
          .put("ssz_generic/uints", new SszGenericUIntTestExecutor())
          .build();
}
