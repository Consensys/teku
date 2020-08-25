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

import static tech.pegasys.teku.validator.remote.BeaconChainEvent.AGGREGATION;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.ATTESTATION;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.IMPORTED_BLOCK;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.ON_SLOT;
import static tech.pegasys.teku.validator.remote.BeaconChainEvent.REORG_OCCURRED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class BeaconChainEventMapper {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorTimingChannel validatorTimingChannel;

  public BeaconChainEventMapper(final ValidatorTimingChannel validatorTimingChannel) {
    this.validatorTimingChannel = validatorTimingChannel;
  }

  void map(final BeaconChainEvent event) {
    switch (event.getName()) {
      case ATTESTATION:
        {
          validatorTimingChannel.onAttestationCreationDue(event.getData());
          break;
        }
      case AGGREGATION:
        {
          validatorTimingChannel.onAttestationAggregationDue(event.getData());
          break;
        }
      case IMPORTED_BLOCK:
        {
          validatorTimingChannel.onBlockImportedForSlot(event.getData());
          break;
        }
      case ON_SLOT:
        {
          validatorTimingChannel.onSlot(event.getData());
          validatorTimingChannel.onBlockProductionDue(event.getData());
          break;
        }
      case REORG_OCCURRED:
        {
          validatorTimingChannel.onChainReorg(event.getData());
          break;
        }
      default:
        {
          LOG.error("Invalid BeaconChainEvent type {}", event.getName());
        }
    }
  }
}
