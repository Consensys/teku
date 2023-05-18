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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DeserializeBlocksTest {

  static Spec spec = TestSpecFactory.createMinimalDeneb();
  DataStructureUtil denebData = new DataStructureUtil(spec);
  public static final DeserializableOneOfTypeDefinition<SignedBlockContainer, BlockContainerBuilder>
      DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS =
          DeserializableOneOfTypeDefinition.object(
                  SignedBlockContainer.class, BlockContainerBuilder.class)
              .withType(
                  SignedBlockContainer.IS_SIGNED_BEACON_BLOCK,
                  s -> !s.contains("blob_sidecars"),
                  spec.getGenesisSchemaDefinitions()
                      .getSignedBeaconBlockSchema()
                      .getJsonTypeDefinition())
              .withType(
                  SignedBlockContainer.IS_SIGNED_BLOCK_CONTENTS,
                  s -> s.contains("blob_sidecars"),
                  SchemaDefinitionsDeneb.required(
                          spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
                      .getSignedBlockContentsSchema()
                      .getJsonTypeDefinition())
              .build();

  public static final DeserializableOneOfTypeDefinition<
          SignedBlindedBlockContainer, BlockContainerBuilder>
      DESERIALIZABLE_ONE_OF_SIGNED_BLINDED_BEACON_BLOCK_OR_SIGNED_BLINDED_BLOCK_CONTENTS =
          DeserializableOneOfTypeDefinition.object(
                  SignedBlindedBlockContainer.class, BlockContainerBuilder.class)
              .withType(
                  SignedBlindedBlockContainer.IS_SIGNED_BLINDED_BEACON_BLOCK,
                  s -> !s.contains("blob_sidecars"),
                  spec.getGenesisSchemaDefinitions()
                      .getSignedBeaconBlockSchema()
                      .getJsonTypeDefinition())
              .withType(
                  SignedBlindedBlockContainer.IS_SIGNED_BLINDED_BLOCK_CONTENTS,
                  s -> s.contains("blob_sidecars"),
                  SchemaDefinitionsDeneb.required(
                          spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
                      .getSignedBlindedBlockContentsSchema()
                      .getJsonTypeDefinition())
              .build();

  @Test
  void shouldDeserializeSignedBeaconBlock() throws JsonProcessingException {

    SignedBeaconBlock randomSignedBeaconBlock = denebData.randomSignedBeaconBlock();

    String serializedSignedBeaconBlock =
        JsonUtil.serialize(
            randomSignedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    final SignedBlockContainer result =
        JsonUtil.parse(
            serializedSignedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBeaconBlock.class);

    assertThat(result.getBlock()).isEqualTo(randomSignedBeaconBlock.getMessage());
    assertThat(result.getSignedBlock()).isEqualTo(randomSignedBeaconBlock);
    assertThat(result.getSignedBlobSidecars()).isEmpty();
  }

  @Test
  void shouldDeserializeSignedBlockContents() throws JsonProcessingException {

    SignedBlockContents randomSignedBlockContents = denebData.randomSignedBlockContents();

    String serializedSignedBlockContents =
        JsonUtil.serialize(
            randomSignedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    final SignedBlockContainer result =
        JsonUtil.parse(
            serializedSignedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);
    assertThat(result).isInstanceOf(SignedBlockContents.class);

    assertThat(result.getSignedBlock()).isEqualTo(randomSignedBlockContents.getSignedBlock());
    assertThat(result.getSignedBlobSidecars()).isPresent();
    assertThat(result.getSignedBlobSidecars().get())
        .hasSize(spec.getMaxBlobsPerBlock().orElseThrow());
  }

  @Test
  void shouldDeserializeSignedBlindedBeaconBlock() throws JsonProcessingException {

    SignedBeaconBlock randomBlindedBeaconBlock = denebData.randomSignedBeaconBlock();

    String serializedSignedBeaconBlock =
        JsonUtil.serialize(
            randomBlindedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BLINDED_BEACON_BLOCK_OR_SIGNED_BLINDED_BLOCK_CONTENTS);

    final SignedBlindedBlockContainer result =
        JsonUtil.parse(
            serializedSignedBeaconBlock,
            DESERIALIZABLE_ONE_OF_SIGNED_BLINDED_BEACON_BLOCK_OR_SIGNED_BLINDED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBeaconBlock.class);

    assertThat(result.getSignedBlock()).isEqualTo(randomBlindedBeaconBlock);
    assertThat(result.getSignedBlindedBlobSidecars()).isEmpty();
  }

  @Test
  void shouldDeserializeBlindedBlockContents() throws JsonProcessingException {

    SignedBlindedBlockContents randomSignedBlindedBlockContents =
        denebData.randomSignedBlindedBlockContents();

    String serializedSignedBlindedBlockContents =
        JsonUtil.serialize(
            randomSignedBlindedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BLINDED_BEACON_BLOCK_OR_SIGNED_BLINDED_BLOCK_CONTENTS);

    final SignedBlindedBlockContainer result =
        JsonUtil.parse(
            serializedSignedBlindedBlockContents,
            DESERIALIZABLE_ONE_OF_SIGNED_BLINDED_BEACON_BLOCK_OR_SIGNED_BLINDED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBlindedBlockContents.class);

    assertThat(result.getSignedBlock())
        .isEqualTo(randomSignedBlindedBlockContents.getSignedBlock());
    assertThat(result.getSignedBlindedBlobSidecars()).isPresent();
    assertThat(result.getSignedBlindedBlobSidecars().get())
        .hasSize(spec.getMaxBlobsPerBlock().orElseThrow());
  }

  private static class BlockContainerBuilder {}
}
