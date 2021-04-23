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

package tech.pegasys.teku.validator.client;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.BlockProductionScheduledDuties;

public class BlockEpochDuties extends EpochDuties<BlockProductionScheduledDuties> {

  protected BlockEpochDuties(
      final DutyLoader<BlockProductionScheduledDuties> dutyLoader, final UInt64 epoch) {
    super(dutyLoader, epoch);
  }

  public static BlockEpochDuties calculateDuties(
      final DutyLoader<BlockProductionScheduledDuties> dutyLoader, final UInt64 epoch) {
    final BlockEpochDuties duties = new BlockEpochDuties(dutyLoader, epoch);
    duties.recalculate();
    return duties;
  }

  public void onBlockProductionDue(final UInt64 slot) {
    execute(duties -> duties.produceBlock(slot));
  }
}
