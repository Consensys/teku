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

package tech.pegasys.artemis.util.time;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;

public class StubTimeProvider implements TimeProvider {

  private UnsignedLong timeInMillis;

  private StubTimeProvider(final UnsignedLong timeInMillis) {
    this.timeInMillis = timeInMillis;
  }

  public static StubTimeProvider withTimeInSeconds(final long timeInSeconds) {
    return withTimeInSeconds(UnsignedLong.valueOf(timeInSeconds));
  }

  public static StubTimeProvider withTimeInSeconds(final UnsignedLong timeInSeconds) {
    return withTimeInMillis(timeInSeconds.times(MILLIS_PER_SECOND));
  }

  public static StubTimeProvider withTimeInMillis(final long timeInMillis) {
    return withTimeInMillis(UnsignedLong.valueOf(timeInMillis));
  }

  public static StubTimeProvider withTimeInMillis(final UnsignedLong timeInMillis) {
    return new StubTimeProvider(timeInMillis);
  }

  public void advanceTimeBySeconds(final long seconds) {
    advanceTimeByMillis(TimeUnit.SECONDS.toMillis(seconds));
  }

  public void advanceTimeByMillis(final long millis) {
    this.timeInMillis = timeInMillis.plus(UnsignedLong.valueOf(millis));
  }

  @Override
  public UnsignedLong getTimeInMillis() {
    return timeInMillis;
  }
}
