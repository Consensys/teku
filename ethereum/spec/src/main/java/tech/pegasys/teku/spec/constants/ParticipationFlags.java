/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.constants;

public class ParticipationFlags {
  public static final int TIMELY_HEAD_FLAG = 1;
  public static final int TIMELY_SOURCE_FLAG = 2;
  public static final int TIMELY_TARGET_FLAG = 4;

  private static final int ALL_FLAGS =
      combineFlags(TIMELY_HEAD_FLAG, TIMELY_SOURCE_FLAG, TIMELY_TARGET_FLAG);

  public static boolean isTimelyTarget(int value) {
    return checkIfAnyFlagIsSet(value, TIMELY_TARGET_FLAG);
  }

  public static boolean isAnyFlagSet(int value) {
    return checkIfAnyFlagIsSet(value, ALL_FLAGS);
  }

  private static boolean checkIfAnyFlagIsSet(final int value, final int flags) {
    return (value & flags) != 0;
  }

  private static int combineFlags(final int... flags) {
    int combined = 0;
    for (int flag : flags) {
      combined = combined | flag;
    }
    return combined;
  }
}
