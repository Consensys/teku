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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszContainerStorage;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBeaconBlockTest {
  @Test
  public void shouldRoundTripViaSsz() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
    final SignedBeaconBlock block = new DataStructureUtil(spec).randomSignedBeaconBlock(1);
    final Bytes ssz = block.sszSerialize();
    final SignedBeaconBlock result =
        schemaDefinitions.getSignedBeaconBlockSchema().sszDeserialize(ssz);
    assertThat(result).isEqualTo(block);
  }

  @Test
  public void shouldStoreComponentsSeparately() {

    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
    final SignedBeaconBlock block = new DataStructureUtil(spec).randomSignedBeaconBlock(1);

    final Map<Bytes32, Bytes> separateStorage = new HashMap<>();
    final SszContainerStorage<SignedBeaconBlock> storageVersion =
        block.toStorageVersion(
            data -> separateStorage.put(data.hashTreeRoot(), data.sszSerialize()));

    final Bytes ssz = storageVersion.sszSerialize();
    assertThat(block.hashTreeRoot()).isEqualTo(storageVersion.hashTreeRoot());
    assertThat(separateStorage).hasSize(1);

    final SszContainerStorage<SignedBeaconBlock> fromStoredSsz =
        schemaDefinitions.getSignedBeaconBlockSchema().asStorageVersion().sszDeserialize(ssz);
    final SignedBeaconBlock result = fromStoredSsz.loadFully(separateStorage::get);
    assertThat(result).isEqualTo(block);
    assertThat(result.sszSerialize()).isEqualTo(block.sszSerialize());
  }
}
