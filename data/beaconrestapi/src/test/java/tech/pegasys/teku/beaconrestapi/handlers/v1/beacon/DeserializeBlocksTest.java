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
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

public class DeserializeBlocksTest {

  static Spec spec = TestSpecFactory.createMinimalDeneb();
  public static final DeserializableOneOfTypeDefinition<
          tech.pegasys.teku.spec.datastructures.blocks.BlockContainer, BlockContainerBuilder>
      DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS =
          DeserializableOneOfTypeDefinition.object(
                  tech.pegasys.teku.spec.datastructures.blocks.BlockContainer.class,
                  BlockContainerBuilder.class)
              .description(
                  "Submit a signed beacon block to the beacon node to be imported."
                      + " The beacon node performs the required validation.")
              .withType(
                  tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock.isInstance,
                  s -> !s.contains("blob_sidecars"),
                  spec.getGenesisSchemaDefinitions()
                      .getSignedBeaconBlockSchema()
                      .getJsonTypeDefinition())
              .withType(
                  tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents
                      .isInstance,
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
  void shouldDeserializeSignedBlockContents() throws JsonProcessingException {
    final tech.pegasys.teku.spec.datastructures.blocks.BlockContainer result =
        JsonUtil.parse(
            readResource("json/signed_block_contents.json"),
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);
    assertThat(result).isInstanceOf(SignedBlockContents.class);

    SignedBlockContents signedBlockContents = (SignedBlockContents) result;

    assertThat(signedBlockContents.getSignedBeaconBlock()).isPresent();
    assertThat(signedBlockContents.getSignedBlobSidecars()).isPresent();
    assertThat(signedBlockContents.getSignedBlobSidecars().get())
        .hasSize(spec.getMaxBlobsPerBlock().orElseThrow());
  }

  @Test
  void shouldDeserializeSignedBeaconBlock() throws JsonProcessingException {
    final BlockContainer result =
        JsonUtil.parse(
            readResource("json/signed_beacon_block.json"),
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBeaconBlock.class);

    SignedBeaconBlock signedBeaconBlock = (SignedBeaconBlock) result;

    assertThat(signedBeaconBlock.getSignedBeaconBlock()).isPresent();
    assertThat(signedBeaconBlock.getSignedBlobSidecars()).isEmpty();
  }

  protected String readResource(final String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static class BlockContainerBuilder {}
}
