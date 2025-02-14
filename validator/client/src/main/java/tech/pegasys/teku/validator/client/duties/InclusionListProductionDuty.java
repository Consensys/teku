/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.client.duties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class InclusionListProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 slot;

  @SuppressWarnings("unused")
  private final Int2ObjectMap<UInt64> assignments = new Int2ObjectOpenHashMap<>();

  @SuppressWarnings("unused")
  private final ValidatorApiChannel validatorApiChannel;

  public InclusionListProductionDuty(
      final UInt64 slot, final ValidatorApiChannel validatorApiChannel) {
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public DutyType getType() {
    return DutyType.INCLUSION_LIST_PRODUCTION;
  }

  // TODO EIP7805 implement IL creation
  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
