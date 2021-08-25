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

package tech.pegasys.teku.validator.client.signer;

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.api.SchemaObjectProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

public class ExternalSignerBlockRequestProvider {
  private final Spec spec;
  private final SchemaObjectProvider schemaObjectProvider;
  private final BeaconBlock block;
  private final SignType signType;

  public ExternalSignerBlockRequestProvider(final Spec spec, final BeaconBlock block) {
    this.spec = spec;
    this.block = block;
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
        schemaObjectProvider.getBeaconBlock(block);
    if (signType == SignType.BLOCK) {
      metadata.put("block", beaconBlock); // backward compatible with phase0
    } else {
      metadata.put(
          "beacon_block",
          new BlockRequestBody(spec.atSlot(block.getSlot()).getMilestone(), beaconBlock));
    }

    return metadata;
  }

  public SignType getSignType() {
    return signType;
  }
}
