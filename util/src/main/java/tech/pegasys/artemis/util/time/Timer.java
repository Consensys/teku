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

package tech.pegasys.artemis.util.time;

@SuppressWarnings({"rawtypes"})
public interface Timer {

  String BLOCK_TIMER_NAME = "BlockTimer";
  String QUARTZ_TIMER_NAME = "QuartzTimer";
  String JAVA_TIMER_NAME = "JavaTimer";

  void start();

  void stop();

  static Class getTimerType(String className) {
    switch (className) {
      case Timer.BLOCK_TIMER_NAME:
        return BlockTimer.class;
      case Timer.QUARTZ_TIMER_NAME:
        return QuartzTimer.class;
      case Timer.JAVA_TIMER_NAME:
        return JavaTimer.class;
      default:
        return QuartzTimer.class;
    }
  }
}
