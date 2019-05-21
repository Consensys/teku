/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.data.adapter;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;

/** Transforms a data record into a time series record */
public class TimeSeriesAdapter implements DataAdapter<TimeSeriesRecord> {
  private static final ALogger LOG = new ALogger(TimeSeriesAdapter.class.getName());
  RawRecord input;

  public TimeSeriesAdapter(RawRecord input) {
    this.input = input;
  }

  @Override
  public TimeSeriesRecord transform() {

    long slot = this.input.getHeadBlock().getSlot();
    // TODO: fix this war crime
    long epoch =
        BeaconStateUtil.slot_to_epoch(UnsignedLong.valueOf(this.input.getHeadBlock().getSlot()))
            .longValue();
    BeaconBlock headBlock = this.input.getHeadBlock();
    BeaconState headState = this.input.getHeadState();
    BeaconBlock justifiedBlock = this.input.getJustifiedBlock();
    BeaconState justifiedState = this.input.getJustifiedState();
    BeaconBlock finalizedBlock = this.input.getFinalizedBlock();
    BeaconState finalizedState = this.input.getFinalizedState();
    long numValidators = headState.getValidator_registry().size();

    Bytes32 headBlockRoot = headBlock.signed_root("signature");
    Bytes32 justifiedBlockRoot = justifiedBlock.signed_root("signature");
    Bytes32 justifiedStateRoot = justifiedState.hash_tree_root();
    Bytes32 finalizedBlockRoot = finalizedBlock.signed_root("signature");
    Bytes32 finalizedStateRoot = finalizedState.hash_tree_root();
    return new TimeSeriesRecord(
        this.input.getIndex(),
        slot,
        epoch,
        headBlockRoot.toHexString(),
        headBlock.getState_root().toHexString(),
        headBlock.getPrevious_block_root().toHexString(),
        numValidators,
        justifiedBlockRoot.toHexString(),
        justifiedStateRoot.toHexString(),
        finalizedBlockRoot.toHexString(),
        finalizedStateRoot.toHexString());
  }
}
