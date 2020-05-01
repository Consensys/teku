package tech.pegasys.artemis.validator.client;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class StableSubnetSubscriberTest {
  private final Map<BLSPublicKey, Validator> validators = new HashMap<>(Map.of(
          BLSPublicKey.random(0), mock(Validator.class),
          BLSPublicKey.random(1), mock(Validator.class)
  ));
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private StableSubnetSubscriber stableSubnetSubscriber;

  @BeforeEach
  void setUp() {

    stableSubnetSubscriber = new StableSubnetSubscriber(
           validatorApiChannel,
            validators
    );
  }

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    verify(validatorApiChannel)
            .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));
  }

  @Test
  void shouldLowerNumberOfSubscriptionsWhenNumberOfValidatorsDecrease() {
    verify(validatorApiChannel)
            .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));

    validators.remove(BLSPublicKey.random(0));

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
            .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 1));
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    verify(validatorApiChannel)
            .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));

    validators.put(BLSPublicKey.random(2), mock(Validator.class));

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
            .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 3));
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

    StableSubnetSubscriber stableSubnetSubscriber = new StableSubnetSubscriber(
            validatorApiChannel,
            Map.of(BLSPublicKey.random(0), mock(Validator.class))
    );

    ArgumentCaptor<Map<Integer, UnsignedLong>> firstSubscriptionUpdate = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Map<Integer, UnsignedLong>> secondSubscriptionUpdate = ArgumentCaptor.forClass(Map.class);

    verify(validatorApiChannel)
            .updateRandomSubnetSubscriptions(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(1);

    UnsignedLong firstUnsubscriptionSlot = (UnsignedLong) new ArrayList(firstSubscriptionUpdate.getValue().values()).get(0);

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UnsignedLong.ONE));

    verifyNoMoreInteractions(validatorApiChannel);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(validatorApiChannel, times(2))
            .updateRandomSubnetSubscriptions(secondSubscriptionUpdate.capture());

    UnsignedLong secondUnsubscriptionSlot = (UnsignedLong) new ArrayList(secondSubscriptionUpdate.getValue().values()).get(0);

    assertThat(firstUnsubscriptionSlot).isNotEqualByComparingTo(secondUnsubscriptionSlot);
  }
}
