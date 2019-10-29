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

package org.ethereum.beacon.chain.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.ethereum.beacon.chain.util.SampleObservableState;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.schedulers.ControlledSchedulers;
import org.ethereum.beacon.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class ObservableStateProcessorTest {

  @Test
  public void test1() throws Exception {
    ControlledSchedulers schedulers = Schedulers.createControlled();
    Duration genesisTime = Duration.ofMinutes(10);
    SlotNumber genesisSlot = SlotNumber.of(1_000_000);
    schedulers.setCurrentTime((genesisTime.getSeconds() + 1) * 1000);

    SampleObservableState sample =
        new SampleObservableState(
            new Random(1),
            genesisTime,
            genesisSlot.getValue(),
            Duration.ofSeconds(10),
            8,
            s -> {},
            schedulers);

    List<ObservableBeaconState> states = new ArrayList<>();
    Flux.from(sample.observableStateProcessor.getObservableStateStream()).subscribe(states::add);

    List<SlotNumber> slots = new ArrayList<>();
    Flux.from(sample.slotTicker.getTickerStream()).subscribe(slots::add);

    System.out.println(states);
    System.out.println(slots);

    assertEquals(1, states.size());
    assertEquals(0, slots.size());

    schedulers.addTime(Duration.ofSeconds(10));

    System.out.println(states);
    System.out.println(slots);

    assertEquals(2, states.size());
    assertEquals(1, slots.size());

    assertEquals(genesisSlot.getValue() + 1, slots.get(0).getValue());
    assertEquals(genesisSlot.increment(), states.get(1).getLatestSlotState().getSlot());
  }

  @Test
  public void test2() throws Exception {
    ControlledSchedulers schedulers = Schedulers.createControlled();
    Duration genesisTime = Duration.ofMinutes(10);
    SlotNumber genesisSlot = SlotNumber.of(1_000_000);
    schedulers.setCurrentTime(genesisTime.plus(Duration.ofMinutes(10)).toMillis());

    SampleObservableState sample =
        new SampleObservableState(
            new Random(1),
            genesisTime,
            genesisSlot.getValue(),
            Duration.ofSeconds(10),
            8,
            s -> {},
            schedulers);

    List<ObservableBeaconState> states = new ArrayList<>();
    Flux.from(sample.observableStateProcessor.getObservableStateStream())
        .subscribe(s -> states.add(s));

    List<SlotNumber> slots = new ArrayList<>();
    Flux.from(sample.slotTicker.getTickerStream()).subscribe(slots::add);

    assertEquals(1, states.size());
    assertEquals(0, slots.size());

    schedulers.addTime(Duration.ofSeconds(10));

    assertEquals(2, states.size());
    assertEquals(1, slots.size());

    assertEquals(genesisSlot.getValue() + 61, slots.get(0).getValue());
    assertEquals(genesisSlot.plus(61), states.get(1).getLatestSlotState().getSlot());
  }
}
