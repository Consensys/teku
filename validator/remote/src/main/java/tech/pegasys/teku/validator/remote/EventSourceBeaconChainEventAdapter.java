package tech.pegasys.teku.validator.remote;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.EventSource.Builder;
import com.launchdarkly.eventsource.MessageEvent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.eventadapter.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final EventSource eventSource;

  public EventSourceBeaconChainEventAdapter(
      final String baseEndpoint,
      final OkHttpClient okHttpClient,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.eventSource =
        new Builder(
                new BeaconEventHandler(validatorTimingChannel),
                HttpUrl.parse(baseEndpoint)
                    .resolve(ValidatorApiMethod.EVENTS.getPath() + "?topics=head,chain_reorg"))
            .client(okHttpClient)
            .build();
  }

  @Override
  public SafeFuture<Void> start() {
    eventSource.start();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    eventSource.close();
    return SafeFuture.COMPLETE;
  }

  private class BeaconEventHandler implements EventHandler {

    private final ValidatorTimingChannel validatorTimingChannel;

    public BeaconEventHandler(final ValidatorTimingChannel validatorTimingChannel) {
      this.validatorTimingChannel = validatorTimingChannel;
    }

    @Override
    public void onOpen() throws Exception {
      System.out.println("Opened");
    }

    @Override
    public void onClosed() throws Exception {
      System.out.println("Closed");
    }

    @Override
    public void onMessage(final String event, final MessageEvent messageEvent) throws Exception {
      System.out.println("Got message of type: " + event + " with: " + messageEvent.getData());
    }

    @Override
    public void onComment(final String comment) throws Exception {
      System.out.println("COMMENT: " + comment);
    }

    @Override
    public void onError(final Throwable t) {
      t.printStackTrace();
    }
  }
}
