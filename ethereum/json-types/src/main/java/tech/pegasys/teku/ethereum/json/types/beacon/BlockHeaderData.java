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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;

public class BlockHeaderData {
  public static final DeserializableTypeDefinition<BlockHeaderData> BLOCK_HEADER_TYPE =
      DeserializableTypeDefinition.object(BlockHeaderData.class, BlockHeaderDataBuilder.class)
          .initializer(BlockHeaderDataBuilder::new)
          .finisher(BlockHeaderDataBuilder::build)
          .withField("root", BYTES32_TYPE, BlockHeaderData::getRoot, BlockHeaderDataBuilder::root)
          .withField(
              "canonical",
              BOOLEAN_TYPE,
              BlockHeaderData::isCanonical,
              BlockHeaderDataBuilder::canonical)
          .withField(
              "header",
              SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition(),
              BlockHeaderData::getHeader,
              BlockHeaderDataBuilder::header)
          .build();

  private final Bytes32 root;
  private final boolean canonical;
  private final SignedBeaconBlockHeader header;

  public BlockHeaderData(
      final Bytes32 root, final boolean canonical, final SignedBeaconBlockHeader header) {
    this.root = root;
    this.canonical = canonical;
    this.header = header;
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
}
