/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DeserializeBlocksTest {

  static Spec spec = TestSpecFactory.createMinimalDeneb();
  DataStructureUtil denebData = new DataStructureUtil(spec);
  public static final DeserializableOneOfTypeDefinition<BlockContainer, BlockContainerBuilder>
      DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS =
          DeserializableOneOfTypeDefinition.object(
                  BlockContainer.class, BlockContainerBuilder.class)
              .withType(
                  SignedBeaconBlock.isInstance,
                  s -> !s.contains("blob_sidecars"),
                  spec.getGenesisSchemaDefinitions()
                      .getSignedBeaconBlockSchema()
                      .getJsonTypeDefinition())
              .withType(
                  SignedBlockContents.isInstance,
                  s -> s.contains("blob_sidecars"),
                  SignedBlockContentsSchema.create(
                          spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow(),
                          SignedBlobSidecarSchema.create(
                              BlobSidecarSchema.create(
                                  new BlobSchema(
                                      spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow()))),
                          spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema())
                      .getJsonTypeDefinition())
              .build();

  @Test
  void shouldDeserializeSignedBeaconBlock() throws JsonProcessingException {

    SignedBeaconBlock randomSignedBeaconBlock = denebData.randomSignedBeaconBlock();

    String serializedSignedBeaconBlock =
        JsonUtil.serialize(
            randomSignedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    final BlockContainer result =
        JsonUtil.parse(
            serializedSignedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBeaconBlock.class);

    assertThat(result.getSignedBeaconBlock()).isPresent();
    assertThat(result.getSignedBlobSidecars()).isEmpty();
  }

  @Test
  void shouldDeserializeSignedBlockContents() throws JsonProcessingException {

    SignedBlockContents randomSignedBlockContents = denebData.randomSignedBlockContents();

    String serializedSignedBlockContents =
        JsonUtil.serialize(
            randomSignedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    final BlockContainer result =
        JsonUtil.parse(
            serializedSignedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);
    assertThat(result).isInstanceOf(SignedBlockContents.class);

    assertThat(result.getSignedBeaconBlock()).isPresent();
    assertThat(result.getSignedBlobSidecars()).isPresent();
    assertThat(result.getSignedBlobSidecars().get())
        .hasSize(spec.getMaxBlobsPerBlock().orElseThrow());
  }

  private static class BlockContainerBuilder {}
}
