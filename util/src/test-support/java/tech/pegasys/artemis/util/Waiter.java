package tech.pegasys.artemis.util;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;

/**
 * A simpler wrapper around Awaitility that directs people towards best practices for waiting. The
 * native Awaitility wrapper has a number of "gotchas" that can lead to intermittency which this
 * wrapper aims to prevent.
 */
public class Waiter {

  public static void waitFor(final Condition assertion) {
    Awaitility.waitAtMost(30, TimeUnit.SECONDS).untilAsserted(assertion::run);
  }

  public interface Condition {
    void run() throws Throwable;
  }
}
