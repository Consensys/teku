/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.api.signer;

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.enumOf;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public record BlockWrapper(
    SpecMilestone milestone, Optional<BeaconBlock> block, Optional<BeaconBlockHeader> blockHeader) {
  public static DeserializableTypeDefinition<BlockWrapper> getJsonTypeDefinition(
      final SchemaDefinitions schemaDefinitions) {
    return DeserializableTypeDefinition.object(BlockWrapper.class, Builder.class)
        .initializer(Builder::new)
        .finisher(Builder::build)
        .withField(
            "version",
            enumOf(SpecMilestone.class).build(),
            BlockWrapper::milestone,
            Builder::milestone)
        .withOptionalField(
            SignType.BLOCK.getName(),
            schemaDefinitions.getBeaconBlockSchema().getJsonTypeDefinition(),
            BlockWrapper::block,
            Builder::block)
        .withOptionalField(
            "block_header",
            BeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition(),
            BlockWrapper::blockHeader,
            Builder::blockHeader)
        .build();
  }

  static class Builder {
    private SpecMilestone milestone;
    private Optional<BeaconBlock> block = Optional.empty();
    private Optional<BeaconBlockHeader> blockHeader = Optional.empty();

    public Builder milestone(final SpecMilestone milestone) {
      this.milestone = milestone;
      return this;
    }

    public Builder block(final Optional<BeaconBlock> block) {
      this.block = block;
      return this;
    }

    public Builder blockHeader(final Optional<BeaconBlockHeader> blockHeader) {
      this.blockHeader = blockHeader;
      return this;
    }

    public BlockWrapper build() {
      return new BlockWrapper(milestone, block, blockHeader);
    }
  }
}
