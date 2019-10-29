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

package org.ethereum.beacon.schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.apache.logging.log4j.ThreadContext;

public class LoggerMDCExecutor implements Executor {

  private final List<String> mdcKeys = new ArrayList<>();
  private final List<Supplier<String>> mdcValueSuppliers = new ArrayList<>();
  private final Executor delegateExecutor;

  public LoggerMDCExecutor() {
    this(Runnable::run);
  }

  public LoggerMDCExecutor(Executor delegateExecutor) {
    this.delegateExecutor = delegateExecutor;
  }

  public LoggerMDCExecutor add(String mdcKey, Supplier<String> mdcValueSupplier) {
    mdcKeys.add(mdcKey);
    mdcValueSuppliers.add(mdcValueSupplier);
    return this;
  }

  @Override
  public void execute(Runnable command) {
    List<String> oldValues = new ArrayList<>(mdcKeys.size());
    for (int i = 0; i < mdcKeys.size(); i++) {
      oldValues.add(ThreadContext.get(mdcKeys.get(i)));
      ThreadContext.put(mdcKeys.get(i), mdcValueSuppliers.get(i).get());
    }
    delegateExecutor.execute(command);
    for (int i = 0; i < mdcKeys.size(); i++) {
      if (oldValues.get(i) == null) {
        ThreadContext.remove(mdcKeys.get(i));
      } else {
        ThreadContext.put(mdcKeys.get(i), oldValues.get(i));
      }
    }
  }
}
