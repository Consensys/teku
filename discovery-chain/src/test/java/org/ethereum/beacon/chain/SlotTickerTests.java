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

package org.ethereum.beacon.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class SlotTickerTests {
  public static final int MILLIS_IN_SECOND = 1000;
  private final Schedulers schedulers;
  SlotTicker slotTicker;
  SlotNumber genesisSlot;
  SlotNumber previousTick = SlotNumber.ZERO;

  public SlotTickerTests() throws InterruptedException {
    schedulers = Schedulers.createDefault();
    MutableBeaconState beaconState = BeaconState.getEmpty().createMutableCopy();
    while (schedulers.getCurrentTime() % MILLIS_IN_SECOND < 100
        || schedulers.getCurrentTime() % MILLIS_IN_SECOND > 900) {
      Thread.sleep(100);
    }
    beaconState.setGenesisTime(
        Time.of(schedulers.getCurrentTime() / MILLIS_IN_SECOND).minus(Time.of(2)));
    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber getGenesisSlot() {
            return SlotNumber.of(12345);
          }

          @Override
          public Time getSecondsPerSlot() {
            return Time.of(1);
          }
        };
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaultHasher(specConstants);
    genesisSlot = spec.getConstants().getGenesisSlot();
    slotTicker = new SlotTicker(spec, beaconState, Schedulers.createDefault());
  }

  @Test
  public void testSlotTicker() throws Exception {
    slotTicker.start();
    final CountDownLatch bothAssertsRun = new CountDownLatch(3);
    Flux.from(slotTicker.getTickerStream())
        .subscribe(
            slotNumber -> {
              if (previousTick.greater(SlotNumber.ZERO)) {
                assertEquals(previousTick.increment(), slotNumber);
                bothAssertsRun.countDown();
              } else {
                assertTrue(slotNumber.greater(genesisSlot)); // first tracked tick
                bothAssertsRun.countDown();
              }
              previousTick = slotNumber;
            });
    //    assertTrue(
    //        String.format("%s assertion(s) was not correct or not tested",
    // bothAssertsRun.getCount()),
    //        bothAssertsRun.await(4, TimeUnit.SECONDS));
  }
}
