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

package tech.pegasys.teku.api.response.v1.beacon;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockHeader {
  @Schema(type = "string", example = EXAMPLE_BYTES32, pattern = PATTERN_BYTES32)
  public final Bytes32 root;

  public final boolean canonical;
  public final SignedBeaconBlockHeader header;

  @JsonCreator
  public BlockHeader(
      @JsonProperty("root") final Bytes32 root,
      @JsonProperty("canonical") final boolean canonical,
      @JsonProperty("header") final SignedBeaconBlockHeader header) {
    this.root = root;
    this.canonical = canonical;
    this.header = header;
  }

  public BlockHeader(final SignedBeaconBlock signedBeaconBlock, final boolean canonical) {
    this.root = signedBeaconBlock.getRoot();
    this.canonical = canonical;
    this.header =
        new SignedBeaconBlockHeader(
            new BeaconBlockHeader(
                signedBeaconBlock.getSlot(),
                signedBeaconBlock.getMessage().getProposerIndex(),
                signedBeaconBlock.getParentRoot(),
                signedBeaconBlock.getStateRoot(),
                signedBeaconBlock.getRoot()),
            new BLSSignature(signedBeaconBlock.getSignature()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BlockHeader that = (BlockHeader) o;
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
    return MoreObjects.toStringHelper(this)
        .add("root", root)
        .add("canonical", canonical)
        .add("header", header)
        .toString();
  }
}
