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

package tech.pegasys.teku.validator.remote;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.AGGREGATION;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.ATTESTATION;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.IMPORTED_BLOCK;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.ON_SLOT;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.REORG_OCCURRED;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class BeaconChainEventMapper {

  private static final Logger LOG = LogManager.getLogger();
  private static final String NAME_FIELD = "name";
  private static final String SLOT_FIELD = "data";
  private static final String COMMON_ANCESTOR_SLOT_FIELD = "commonAncestorSlot";

  private final ValidatorTimingChannel validatorTimingChannel;

  public BeaconChainEventMapper(final ValidatorTimingChannel validatorTimingChannel) {
    this.validatorTimingChannel = validatorTimingChannel;
  }

  void map(final Map<String, Object> event) {
    checkArgument(event.containsKey(NAME_FIELD), "Event missing name field");
    checkArgument(event.containsKey(SLOT_FIELD), "Event missing data field");
    final UInt64 slot = UInt64.valueOf(event.get(SLOT_FIELD).toString());
    final String name = event.get(NAME_FIELD).toString();
    switch (name) {
      case ATTESTATION:
      case IMPORTED_BLOCK:
        {
          validatorTimingChannel.onAttestationCreationDue(slot);
          break;
        }
      case AGGREGATION:
        {
          validatorTimingChannel.onAttestationAggregationDue(slot);
          break;
        }
      case ON_SLOT:
        {
          validatorTimingChannel.onSlot(slot);
          validatorTimingChannel.onBlockProductionDue(slot);
          break;
        }
      case REORG_OCCURRED:
        {
          checkArgument(
              event.containsKey(COMMON_ANCESTOR_SLOT_FIELD),
              "Reorg event missing commonAncestorSlot");
          validatorTimingChannel.onChainReorg(
              slot, UInt64.valueOf(event.get(COMMON_ANCESTOR_SLOT_FIELD).toString()));
          break;
        }
      default:
        {
          LOG.error("Invalid BeaconChainEvent type {}", name);
        }
    }
  }
}
