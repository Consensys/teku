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

package tech.pegasys.artemis.events;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;

public enum LoggingChannelExceptionHandler implements ChannelExceptionHandler {
  LOGGING_EXCEPTION_HANDLER;

  @Override
  public void handleException(
      final Throwable error,
      final Object subscriber,
      final Method invokedMethod,
      final Object[] args) {
    LogManager.getLogger(invokedMethod.getClass())
        .error(
            "Unhandled error in subscriber "
                + subscriber.getClass().getName()
                + " for method "
                + invokedMethod.getDeclaringClass().getName()
                + "."
                + invokedMethod.getName()
                + " with arguments "
                + Arrays.toString(args),
            error);
  }
}
