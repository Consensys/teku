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

package tech.pegasys.teku.ssz.backing;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

public interface SszListTestBase extends SszCollectionTestBase {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void sszSerialize_emptyNonBitListShouldResultInEmptySsz(SszList<?> data) {
    Assumptions.assumeTrue(data.isEmpty());
    Assumptions.assumeTrue(data.getSchema().getElementSchema() != SszPrimitiveSchemas.BIT_SCHEMA);
    assertThat(data.sszSerialize()).isEqualTo(Bytes.EMPTY);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void hashTreeRoot_testEmptyListHash(SszList<?> data) {
    Assumptions.assumeTrue(data.isEmpty());

    assertThat(data.hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(
                Bytes.concatenate(
                    TreeUtil.ZERO_TREES[data.getSchema().treeDepth()].hashTreeRoot(),
                    Bytes32.ZERO)));
  }
}
