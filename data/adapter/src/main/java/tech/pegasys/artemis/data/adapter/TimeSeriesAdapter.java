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
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.ValidatorJoin;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

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
    // long numValidators = headState.getValidator_registry().size();

    Bytes32 lastJustifiedBlockRoot = HashTreeUtil.hash_tree_root(justifiedBlock.toBytes());
    Bytes32 lastJustifiedStateRoot = HashTreeUtil.hash_tree_root(justifiedState.toBytes());
    Bytes32 lastFinalizedBlockRoot = HashTreeUtil.hash_tree_root(finalizedBlock.toBytes());
    Bytes32 lastFinalizedStateRoot = HashTreeUtil.hash_tree_root(finalizedState.toBytes());

    List<ValidatorJoin> validators = new ArrayList<ValidatorJoin>();

    IntStream.range(0, headState.getValidator_registry().size())
        .parallel()
        .forEach(
            i -> {
              validators.add(
                  new ValidatorJoin(
                      headState.getValidator_registry().get(i),
                      headState.getValidator_balances().get(i)));
            });
    return new TimeSeriesRecord(
        this.input.getIndex(),
        slot,
        epoch,
        this.input.getHeadBlock().getState_root().toHexString(),
        this.input.getHeadBlock().getParent_root().toHexString(),
        HashTreeUtil.hash_tree_root(this.input.getHeadBlock().toBytes()).toHexString(),
        lastJustifiedBlockRoot.toHexString(),
        lastJustifiedStateRoot.toHexString(),
        lastFinalizedBlockRoot.toHexString(),
        lastFinalizedStateRoot.toHexString(),
        validators);
  }
}
