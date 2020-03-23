package tech.pegasys.artemis.util.time;

import java.util.Date;

public interface TimeEventsChannel {
  void onTick(Date event);
}
