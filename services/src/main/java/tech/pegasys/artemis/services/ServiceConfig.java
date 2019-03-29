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

package tech.pegasys.artemis.services;

import com.google.common.eventbus.EventBus;
import java.util.Objects;
import net.consensys.cava.crypto.SECP256K1;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ServiceConfig {
  EventBus eventBus;
  ArtemisConfiguration config;
  SECP256K1.KeyPair keyPair;
  CommandLineArguments cliArgs;

  public ServiceConfig() {}

  public ServiceConfig(
      EventBus eventBus, ArtemisConfiguration config, CommandLineArguments cliArgs) {
    this.eventBus = eventBus;
    this.config = config;
    this.keyPair = config.getKeyPair();
    this.cliArgs = cliArgs;
  }

  public CommandLineArguments getCliArgs() {
    return cliArgs;
  }

  public EventBus getEventBus() {
    return this.eventBus;
  }

  public void setEventBus(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public ArtemisConfiguration getConfig() {
    return this.config;
  }

  public void setConfig(ArtemisConfiguration config) {
    this.config = config;
  }

  public SECP256K1.KeyPair getKeyPair() {
    return this.keyPair;
  }

  public void setKeyPair(SECP256K1.KeyPair keyPair) {
    this.keyPair = keyPair;
  }

  public ServiceConfig eventBus(EventBus eventBus) {
    this.eventBus = eventBus;
    return this;
  }

  public ServiceConfig config(ArtemisConfiguration config) {
    this.config = config;
    return this;
  }

  public ServiceConfig keyPair(SECP256K1.KeyPair keyPair) {
    this.keyPair = keyPair;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ServiceConfig)) {
      return false;
    }
    ServiceConfig serviceConfig = (ServiceConfig) o;
    return Objects.equals(eventBus, serviceConfig.eventBus)
        && Objects.equals(config, serviceConfig.config)
        && Objects.equals(keyPair, serviceConfig.keyPair);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventBus, config, keyPair);
  }
}
