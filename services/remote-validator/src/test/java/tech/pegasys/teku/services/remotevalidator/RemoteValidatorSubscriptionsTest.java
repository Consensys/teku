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

package tech.pegasys.teku.services.remotevalidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.util.introspection.FieldSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.teku.services.remotevalidator.RemoteValidatorSubscriptions.SubscriptionStatus;
import tech.pegasys.teku.util.config.TekuConfiguration;

@ExtendWith(MockitoExtension.class)
class RemoteValidatorSubscriptionsTest {

  private static final int MAX_SUBSCRIBERS = 2;

  @Mock private TekuConfiguration configuration;

  @Mock private Consumer<BeaconChainEvent> subscriberCallback;

  private RemoteValidatorSubscriptions subscriptions;

  @BeforeEach
  public void beforeEach() {
    when(configuration.getRemoteValidatorApiMaxSubscribers()).thenReturn(MAX_SUBSCRIBERS);

    subscriptions = new RemoteValidatorSubscriptions(configuration);
  }

  @Test
  public void whenSuccessfullySubscribingValidator_shouldReturnSuccess() {
    final SubscriptionStatus subscriptionStatus = subscriptions.subscribe("1", subscriberCallback);

    assertThat(subscriptionStatus.hasSubscribed()).isTrue();
    assertThat(subscriptionStatus.getInfo()).isEqualTo("ok");
  }

  @Test
  public void whenMaxValidatorsHaveSubscribed_shouldReturnMaxValidatorsFailures() {
    subscriptions.subscribe("1", subscriberCallback);
    subscriptions.subscribe("2", subscriberCallback);

    assertThat(internalSubscriptionsMap()).hasSize(MAX_SUBSCRIBERS);

    final SubscriptionStatus subscriptionStatus =
        this.subscriptions.subscribe("3", subscriberCallback);

    assertThat(subscriptionStatus.hasSubscribed()).isFalse();
    assertThat(subscriptionStatus.getInfo()).isEqualTo("Reached max subscribers");
  }

  @Test
  public void whenUnsubscribing_shouldRemoveCorrectValidatorFromInternalMap() {
    subscriptions.subscribe("1", subscriberCallback);
    subscriptions.subscribe("2", subscriberCallback);

    assertThat(internalSubscriptionsMap()).hasSize(2);

    subscriptions.unsubscribe("1");

    assertThat(internalSubscriptionsMap()).containsOnlyKeys("2");
  }

  @Test
  public void whenUnsubscribingAll_shouldEmptyInternalMap() {
    subscriptions.subscribe("1", subscriberCallback);
    subscriptions.subscribe("2", subscriberCallback);

    assertThat(internalSubscriptionsMap()).hasSize(2);

    subscriptions.unsubscribeAll();

    assertThat(internalSubscriptionsMap()).isEmpty();
  }

  @Test
  public void onEvent_ShouldInvokeAllSubscribersCallbacks() {
    subscriptions.subscribe("1", subscriberCallback);
    subscriptions.subscribe("2", subscriberCallback);

    subscriptions.onEvent(mock(BeaconChainEvent.class));

    verify(subscriberCallback, times(2)).accept(any(BeaconChainEvent.class));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Consumer<BeaconChainEvent>> internalSubscriptionsMap() {
    return FieldSupport.EXTRACTION.fieldValue("subscriptions", Map.class, this.subscriptions);
  }
}
