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

package tech.pegasys.artemis.services.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;

public class ServiceAdapterTest {

  private List<Object> receivedEvents = new ArrayList<>();

  @Test
  public void testForwardValidationEvent() throws IOException, InterruptedException {

    final ServiceAdapter adapter1 =
        new ServiceAdapter(30000, Collections.singleton(Deposit.class), Collections.emptySet());

    final EventBus eventBus1 = new EventBus("bus1");
    eventBus1.register(this);
    ServiceConfig config = new ServiceConfig();
    config.setEventBus(eventBus1);
    adapter1.init(config);
    adapter1.run();

    final OutboundEvent<Deposit> outboundEvent =
        new OutboundEvent<>(Deposit.class, "dns:///localhost:30000");

    final ServiceAdapter adapter2 =
        new ServiceAdapter(Collections.emptySet(), Collections.singleton(outboundEvent));

    final EventBus eventBus2 = new EventBus("bus2");
    ServiceConfig config2 = new ServiceConfig();
    config2.setEventBus(eventBus2);
    adapter2.init(config2);
    adapter2.run();

    final Deposit validatorRegistration = createValidatorRegistration(1);
    eventBus2.post(validatorRegistration);

    Thread.sleep(2000);

    assertEquals(1, receivedEvents.size());

    assertValidatorRegistration(validatorRegistration, (Deposit) receivedEvents.get(0));
  }

  @Disabled
  @Test
  public void testTwoWayEvent() throws IOException, InterruptedException {

    ServiceAdapter adapter1 =
        new ServiceAdapter(30002, Collections.singleton(Deposit.class), Collections.emptySet());

    final EventBus eventBus1 = new EventBus("bus1");
    eventBus1.register(this);
    ServiceConfig config = new ServiceConfig();
    config.setEventBus(eventBus1);
    adapter1.init(config);
    adapter1.run();

    final OutboundEvent<Deposit> outboundEvent2to1 =
        new OutboundEvent<>(Deposit.class, "dns:///localhost:30002");

    ServiceAdapter adapter2 =
        new ServiceAdapter(30003, Collections.emptySet(), Collections.singleton(outboundEvent2to1));

    final EventBus eventBus2 = new EventBus("bus2");
    ServiceConfig config2 = new ServiceConfig();
    config2.setEventBus(eventBus2);
    adapter2.init(config2);
    adapter2.run();

    Integer index = 1;
    // Test adapter2 -> adapter1
    final Deposit validatorRegistration2to1 = createValidatorRegistration(index);
    eventBus2.post(validatorRegistration2to1);

    Thread.sleep(2000);

    assertEquals(1, receivedEvents.size());
    Deposit rcvdEvent2to1 = (Deposit) receivedEvents.get(0);
    assertValidatorRegistration(validatorRegistration2to1, rcvdEvent2to1);
    Integer rcvdIndex2to1 = Integer.valueOf(rcvdEvent2to1.getResponse().log.getLogIndexRaw());
    assertEquals(Integer.valueOf(1), rcvdIndex2to1);

    // Test adapter1 -> adapter2
    adapter2 =
        new ServiceAdapter(30003, Collections.singleton(Deposit.class), Collections.emptySet());

    OutboundEvent<Deposit> outboundEvent1to2 =
        new OutboundEvent<>(Deposit.class, "dns:///localhost:30003");

    adapter1 =
        new ServiceAdapter(30002, Collections.emptySet(), Collections.singleton(outboundEvent1to2));

    final Deposit validatorRegistration1to2 = createValidatorRegistration(rcvdIndex2to1 + 1);

    eventBus1.post(validatorRegistration1to2);

    Thread.sleep(2000);

    assertEquals(2, receivedEvents.size());

    Deposit rcvdEvent1to2 = (Deposit) receivedEvents.get(1);
    assertValidatorRegistration(validatorRegistration1to2, rcvdEvent1to2);
    Integer rcvdIndex1to2 = Integer.valueOf(rcvdEvent1to2.getResponse().log.getLogIndexRaw());
    assertEquals(Integer.valueOf(rcvdIndex2to1 + 1), rcvdIndex1to2);
  }

  @Subscribe
  public void onEvent(Object event) {
    receivedEvents.add(event);
  }

  private Deposit createValidatorRegistration(Integer index) {
    DepositContract.DepositEventResponse response = new DepositContract.DepositEventResponse();

    response.data = "data".getBytes(Charset.defaultCharset());
    response.merkle_tree_index = BigInteger.TEN.toByteArray();

    response.log =
        new Log(
            true,
            index.toString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            Collections.singletonList(randomString()));
    return new Deposit(response);
  }

  private Eth2Genesis createEth2Genesis(Integer index) {
    DepositContract.Eth2GenesisEventResponse response =
        new DepositContract.Eth2GenesisEventResponse();
    response.log =
        new Log(
            true,
            index.toString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            Collections.singletonList(randomString()));
    response.time = "time".getBytes(Charset.defaultCharset());
    response.deposit_root = "root".getBytes(Charset.defaultCharset());
    final Eth2Genesis deposit = new Eth2Genesis(response);
    return deposit;
  }

  private void assertValidatorRegistration(Deposit expected, Deposit actual) {

    assertEquals(true, Arrays.equals(expected.getResponse().data, actual.getResponse().data));
    //          assertEquals(
    //              true,
    //              Arrays.equals(
    //                  expected.getResponse().previous_receipt_root,
    //                  actual.getResponse().previous_receipt_root));

    final Log expectedLog = expected.getResponse().log;
    final Log actualLog = actual.getResponse().log;

    assertEquals(expectedLog.getAddress(), actualLog.getAddress());
    assertEquals(expectedLog.getBlockHash(), actualLog.getBlockHash());
    assertEquals(expectedLog.getBlockNumberRaw(), actualLog.getBlockNumberRaw());
    assertEquals(expectedLog.getData(), actualLog.getData());
    assertEquals(expectedLog.getLogIndexRaw(), actualLog.getLogIndexRaw());
    assertEquals(expectedLog.getTransactionHash(), actualLog.getTransactionHash());
    assertEquals(expectedLog.getTransactionIndexRaw(), actualLog.getTransactionIndexRaw());
    assertEquals(expectedLog.getType(), actualLog.getType());
    assertEquals(expectedLog.isRemoved(), actualLog.isRemoved());
    assertEquals(expectedLog.getTopics(), actualLog.getTopics());
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }
}
