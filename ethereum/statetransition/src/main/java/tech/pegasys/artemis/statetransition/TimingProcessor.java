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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.metrics.SettableGauge;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.events.ValidatorAssignmentEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.alogger.ALogger;

public class TimingProcessor {

  private final EventBus eventBus;
  private final SettableGauge currentSlotGauge;
  private final SettableGauge currentEpochGauge;
  private ChainStorageClient chainStorageClient;
  private UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private static final ALogger STDOUT = new ALogger("stdout");

  public TimingProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.chainStorageClient = chainStorageClient;
    currentSlotGauge =
        SettableGauge.create(
            config.getMetricsSystem(),
            ArtemisMetricCategory.BEACONCHAIN,
            "current_slot",
            "Latest slot recorded by the beacon chain");
    currentEpochGauge =
        SettableGauge.create(
            config.getMetricsSystem(),
            ArtemisMetricCategory.BEACONCHAIN,
            "current_epoch",
            "Latest epoch recorded by the beacon chain");
    this.eventBus.register(this);
  }

  @Subscribe
  private void onDBStoreValidEvent(DBStoreValidEvent event) {
    final UnsignedLong slot = event.getNodeSlot();
    STDOUT.log(Level.INFO, "Restoring nodeSlot to: " + slot);
    this.nodeSlot = slot;
  }

  @Subscribe
  private void onTick(Date date) {
    try {
      final UnsignedLong currentTime = UnsignedLong.valueOf(date.getTime() / 1000);
      if (chainStorageClient.getStore() != null) {
        final Transaction transaction = chainStorageClient.getStore().startTransaction();
        on_tick(transaction, currentTime);
        transaction.commit();
        if (chainStorageClient
                .getStore()
                .getTime()
                .compareTo(
                    chainStorageClient
                        .getGenesisTime()
                        .plus(nodeSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0) {
          this.eventBus.post(new SlotEvent(nodeSlot));
          this.currentSlotGauge.set(nodeSlot.longValue());
          this.currentEpochGauge.set(compute_epoch_of_slot(nodeSlot).longValue());
          STDOUT.log(Level.INFO, "******* Slot Event *******", ALogger.Color.WHITE);
          STDOUT.log(Level.INFO, "Node slot:                             " + nodeSlot);
          Thread.sleep(SECONDS_PER_SLOT * 1000 / 2);
          this.eventBus.post(new ValidatorAssignmentEvent(nodeSlot));
          nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
        }
      }
    } catch (InterruptedException e) {
      STDOUT.log(Level.FATAL, "onTick: " + e.toString());
    }
  }
}
