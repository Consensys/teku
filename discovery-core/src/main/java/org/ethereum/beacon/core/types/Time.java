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

package org.ethereum.beacon.core.types;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

/** Time in seconds. */
@SSZSerializable(serializeAs = UInt64.class)
public class Time extends UInt64 implements SafeComparable<Time> {
  private static final SimpleDateFormat SHORT_TIME_FORMAT = createTimeFormat("MM/dd/yy HH:mm:ss");

  private static SimpleDateFormat createTimeFormat(String pattern) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
    // set GMT timezone, to avoid confusion when local TZ is not GMT
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return simpleDateFormat;
  }

  public static final Time ZERO = of(0);

  public static Time of(long seconds) {
    return new Time(UInt64.valueOf(seconds));
  }

  public static Time castFrom(UInt64 time) {
    return new Time(time);
  }

  public Time(UInt64 uint) {
    super(uint);
  }

  public Time plus(Time addend) {
    return new Time(super.plus(addend));
  }

  public Time minus(Time subtrahend) {
    return new Time(super.minus(subtrahend));
  }

  @Override
  public Time times(UInt64 unsignedMultiplier) {
    return new Time(super.times(unsignedMultiplier));
  }

  public Time times(int times) {
    return new Time(super.times(times));
  }

  @Override
  public Time dividedBy(UInt64 divisor) {
    return new Time(super.dividedBy(divisor));
  }

  @Override
  public Time dividedBy(long divisor) {
    return new Time(super.dividedBy(divisor));
  }

  public Millis getMillis() {
    return Millis.of(getValue() * 1000);
  }

  @Override
  public String toString() {
    return SHORT_TIME_FORMAT.format(new Date(getMillis().getValue()));
  }
}
