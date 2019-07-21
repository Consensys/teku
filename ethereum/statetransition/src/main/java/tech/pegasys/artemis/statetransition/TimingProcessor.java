package tech.pegasys.artemis.statetransition;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

import java.util.Date;

import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

public class TimingProcessor {

  private final EventBus eventBus;
  private ChainStorageClient chainStorageClient;
  private UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private static final ALogger STDOUT = new ALogger("stdout");

  public TimingProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.chainStorageClient = chainStorageClient;
  }

  @Subscribe
  private void onTick(Date date) {
    try {
      on_tick(chainStorageClient.getStore(), UnsignedLong.valueOf(date.getTime() / 1000));
      if (chainStorageClient.getStore().getTime()
              .compareTo(chainStorageClient.getGenesisTime().plus(nodeSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT)))) >= 0) {
        this.eventBus.post(new SlotEvent(nodeSlot));
        STDOUT.log(Level.INFO, "******* Slot Event *******", ALogger.Color.WHITE);
        STDOUT.log(Level.INFO, "Node slot:                             " + nodeSlot);
        nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
        Thread.sleep(SECONDS_PER_SLOT * 1000 / 2);
        this.eventBus.post(new ValidatorAssignmentEvent());
      }
    } catch (InterruptedException e) {
      STDOUT.log(Level.FATAL, "onTick: " + e.toString());
    }
  }

}
