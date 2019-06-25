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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.ValidatorJoin;
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

    UnsignedLong slot = this.input.getHeadBlock().getSlot();
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(slot);
    BeaconBlock headBlock = this.input.getHeadBlock();
    BeaconState headState = this.input.getHeadState();
    BeaconBlock justifiedBlock = this.input.getJustifiedBlock();
    BeaconState justifiedState = this.input.getJustifiedState();
    BeaconBlock finalizedBlock = this.input.getFinalizedBlock();
    BeaconState finalizedState = this.input.getFinalizedState();
    int numValidators = headState.getValidator_registry().size();

    Bytes32 lastJustifiedBlockRoot = justifiedBlock.signing_root("signature");
    Bytes32 lastJustifiedStateRoot = justifiedState.hash_tree_root();
    Bytes32 lastFinalizedBlockRoot = finalizedBlock.signing_root("signature");
    Bytes32 lastFinalizedStateRoot = finalizedState.hash_tree_root();

    List<ValidatorJoin> validators = new ArrayList<>();

    if (numValidators > 0) {
      IntStream.range(0, numValidators - 1)
          .forEach(
              i ->
                  validators.add(
                      new ValidatorJoin(
                          headState.getValidator_registry().get(i),
                          headState.getBalances().get(i).longValue())));
    }

    return new TimeSeriesRecord(
        this.input.getDate(),
        this.input.getIndex(),
        slot.longValue(),
        epoch.longValue(),
        this.input.getHeadBlock().getState_root().toHexString(),
        this.input.getHeadBlock().getParent_root().toHexString(),
        this.input.getHeadBlock().signing_root("signature").toHexString(),
        lastJustifiedBlockRoot.toHexString(),
        lastJustifiedStateRoot.toHexString(),
        lastFinalizedBlockRoot.toHexString(),
        lastFinalizedStateRoot.toHexString(),
        validators);
  }
}
