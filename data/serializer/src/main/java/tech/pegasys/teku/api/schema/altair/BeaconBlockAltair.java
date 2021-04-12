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

package tech.pegasys.teku.api.schema.altair;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconBlockAltair extends BeaconBlock {
  private final BeaconBlockBodyAltair altairBody;

  @Override
  public BeaconBlockBody getBody() {
    return altairBody;
  }

  public BeaconBlockAltair(final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock message) {
    super(message);
    if (!(message.getBody() instanceof BeaconBlockBodyAltair)) {
      throw new IllegalArgumentException("Beacon block body was not altair compatible");
    }
    this.altairBody = new BeaconBlockBodyAltair(message.getBody());
  }

  public BeaconBlockAltair(
      final UInt64 slot,
      final UInt64 proposer_index,
      final Bytes32 parent_root,
      final Bytes32 state_root,
      final BeaconBlockBodyAltair body) {
    super(slot, proposer_index, parent_root, state_root, body);
    this.altairBody = body;
  }
}
