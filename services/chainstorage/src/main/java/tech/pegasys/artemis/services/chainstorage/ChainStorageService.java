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

package tech.pegasys.artemis.services.chainstorage;

import com.google.common.eventbus.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.services.ServiceInterface;

public class ChainStorageService implements ServiceInterface {
  private EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger();

  public ChainStorageService() {}

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  @Override
  public void run() {
    // TODO Do something.
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }
}
