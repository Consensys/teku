/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.migrated;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;

public class BlockHeaderData {
  private final Bytes32 root;
  private final boolean canonical;
  private final SignedBeaconBlockHeader header;

  private static final SerializableTypeDefinition<BlockHeaderData> HEADER_DATA_TYPE =
      SerializableTypeDefinition.object(BlockHeaderData.class)
          .withField("root", BYTES32_TYPE, BlockHeaderData::getRoot)
          .withField("canonical", BOOLEAN_TYPE, BlockHeaderData::isCanonical)
          .withField(
              "header",
              SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition(),
              BlockHeaderData::getHeader)
          .build();

  public BlockHeaderData(
      final Bytes32 root, final boolean canonical, final SignedBeaconBlockHeader header) {
    this.root = root;
    this.canonical = canonical;
    this.header = header;
  }

  public BlockHeaderData(final BlockAndMetaData blockAndMetaData) {
    final SignedBeaconBlock signedBeaconBlock = blockAndMetaData.getData();
    final BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(
            signedBeaconBlock.getSlot(),
            signedBeaconBlock.getMessage().getProposerIndex(),
            signedBeaconBlock.getParentRoot(),
            signedBeaconBlock.getStateRoot(),
            signedBeaconBlock.getBodyRoot());
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        new SignedBeaconBlockHeader(beaconBlockHeader, signedBeaconBlock.getSignature());

    this.root = signedBeaconBlock.getRoot();
    this.canonical = blockAndMetaData.isCanonical();
    this.header = signedBeaconBlockHeader;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public boolean isCanonical() {
    return canonical;
  }

  public SignedBeaconBlockHeader getHeader() {
    return header;
  }

  public static SerializableTypeDefinition<BlockHeaderData> getJsonTypeDefinition() {
    return HEADER_DATA_TYPE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockHeaderData that = (BlockHeaderData) o;
    return canonical == that.canonical
        && Objects.equals(root, that.root)
        && Objects.equals(header, that.header);
  }

  @Override
  public int hashCode() {
    return Objects.hash(root, canonical, header);
  }

  @Override
  public String toString() {
    return "BlockHeaderData{"
        + "root="
        + root
        + ", canonical="
        + canonical
        + ", header="
        + header
        + '}';
  }
}
