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

package tech.pegasys.teku.reference.deneb.merkle_proof;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;

public class MerkleProofTests {
  public static final ImmutableMap<String, TestExecutor> MERKLE_PROOF_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("merkle_proof/single_merkle_proof", new SingleMerkleProofTestExecutor())
          .build();
}
