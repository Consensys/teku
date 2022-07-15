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

package tech.pegasys.teku.validator.client.signer;

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.api.SchemaObjectProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;

public class ExternalSignerBlockRequestProvider {
  private final Spec spec;
  private final SchemaObjectProvider schemaObjectProvider;
  private final BeaconBlock block;
  private final BeaconBlockHeader blockHeader;

  private final SignType signType;

  public ExternalSignerBlockRequestProvider(final Spec spec, final BeaconBlock block) {
    this.spec = spec;
    this.block = block;
    this.blockHeader = BeaconBlockHeader.fromBlock(block);
    schemaObjectProvider = new SchemaObjectProvider(spec);
    // backward compatible with phase 0
    if (spec.atSlot(block.getSlot()).getMilestone().equals(SpecMilestone.PHASE0)) {
      signType = SignType.BLOCK;
    } else {
      signType = SignType.BLOCK_V2;
    }
  }

  public Map<String, Object> getBlockMetadata(final Map<String, Object> additionalEntries) {
    final Map<String, Object> metadata = new HashMap<>(additionalEntries);

    final tech.pegasys.teku.api.schema.BeaconBlock beaconBlock =
        block.getBody().isBlinded()
            ? schemaObjectProvider.getBlindedBlock(block)
            : schemaObjectProvider.getBeaconBlock(block);
    final tech.pegasys.teku.api.schema.BeaconBlockHeader beaconBlockHeader =
        new tech.pegasys.teku.api.schema.BeaconBlockHeader(blockHeader);

    final SpecMilestone milestone = spec.atSlot(block.getSlot()).getMilestone();
    switch (milestone) {
      case PHASE0:
        metadata.put("block", beaconBlock); // backward compatible with phase0
        break;
      case ALTAIR:
        metadata.put("beacon_block", new BlockRequestBody(milestone, beaconBlock));
        break;
      default:
        // use block header for BELLATRIX and onward milestones
        metadata.put("beacon_block", new BlockRequestBody(milestone, beaconBlockHeader));
    }

    return metadata;
  }

  public SignType getSignType() {
    return signType;
  }
}
