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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class DirectEventDeliverer<T> extends EventDeliverer<T> {
  private final ChannelExceptionHandler exceptionHandler;

  DirectEventDeliverer(final ChannelExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  protected void deliverTo(final T subscriber, final Method method, final Object[] args) {
    try {
      method.invoke(subscriber, args);
    } catch (IllegalAccessException e) {
      exceptionHandler.handleException(e, subscriber, method, args);
    } catch (InvocationTargetException e) {
      exceptionHandler.handleException(e.getTargetException(), subscriber, method, args);
    }
  }
}
