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
