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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;
import tech.pegasys.artemis.pow.api.PowEvent;
import tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract.Eth1DepositEventResponse;
import tech.pegasys.artemis.pow.event.ValidatorRegistration;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;
import tech.pegasys.artemis.services.adapter.event.ValidatorRegisteredEventDescriptor;

public class ServiceAdapterTest {

  private List<PowEvent> receivedEvents = new ArrayList<>();

  @Test
  public void testForwardValidationEvent() throws IOException, InterruptedException {

    final ServiceAdapter adapter1 =
        new ServiceAdapter(
            30000,
            Collections.singleton(new ValidatorRegisteredEventDescriptor()),
            Collections.emptySet());

    final EventBus eventBus1 = new EventBus("bus1");
    eventBus1.register(this);

    adapter1.init(eventBus1);
    adapter1.run();

    final OutboundEvent<ValidatorRegistration> outboundEvent =
        new OutboundEvent<>(new ValidatorRegisteredEventDescriptor(), "dns:///localhost:30000");

    final ServiceAdapter adapter2 =
        new ServiceAdapter(Collections.emptySet(), Collections.singleton(outboundEvent));

    final EventBus eventBus2 = new EventBus("bus2");
    adapter2.init(eventBus2);
    adapter2.run();

    final ValidatorRegistration validatorRegistration = createValidatorRegistration();
    eventBus2.post(validatorRegistration);

    Thread.sleep(2000);

    assertEquals(1, receivedEvents.size());

    assertValidatorRegistration(
        validatorRegistration, (ValidatorRegistration) receivedEvents.get(0));
  }

  @Subscribe
  public void onEvent(PowEvent event) {
    receivedEvents.add(event);
  }

  private ValidatorRegistration createValidatorRegistration() {
    final Eth1DepositEventResponse deposit = new Eth1DepositEventResponse();

    deposit.data = "data".getBytes();
    deposit.deposit_count = BigInteger.TEN;
    deposit.previous_receipt_root = "root".getBytes();

    deposit.log =
        new Log(
            true,
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            Collections.singletonList(randomString()));

    return new ValidatorRegistration(deposit);
  }

  private void assertValidatorRegistration(
      ValidatorRegistration expected, ValidatorRegistration actual) {

    assertEquals(expected.getType(), actual.getType());
    assertEquals(true, Arrays.equals(expected.getResponse().data, actual.getResponse().data));
    assertEquals(
        true,
        Arrays.equals(
            expected.getResponse().previous_receipt_root,
            actual.getResponse().previous_receipt_root));

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
