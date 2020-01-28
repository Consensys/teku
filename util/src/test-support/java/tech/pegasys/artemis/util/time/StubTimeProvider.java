package tech.pegasys.artemis.util.time;

import com.google.common.primitives.UnsignedLong;

public class StubTimeProvider extends TimeProvider {

  private UnsignedLong timeInSeconds;

  public StubTimeProvider() {
    this(UnsignedLong.valueOf(29842948));
  }

  public StubTimeProvider(final long timeInSeconds) {
    this(UnsignedLong.valueOf(timeInSeconds));
  }

  public StubTimeProvider(final UnsignedLong timeInSeconds) {
    this.timeInSeconds = timeInSeconds;
  }

  public void advanceTimeBySeconds(final long seconds) {
    this.timeInSeconds = timeInSeconds.plus(UnsignedLong.valueOf(seconds));
  }

  @Override
  public UnsignedLong getTimeInSeconds() {
    return timeInSeconds;
  }
}