/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.infrastructure.logging;

import org.apache.logging.log4j.Logger;

public class StartupLogConfig {
  private final String network;
  private final String storageMode;
  private final int restApiPort;

  public StartupLogConfig(final String network, final String storageMode, final int restApiPort) {
    this.network = network;
    this.storageMode = storageMode;
    this.restApiPort = restApiPort;
  }

  public void report(final Logger log) {
    log.info("Configuration"); // TODO clean up formatting here
    log.info("Network: " + network);
    log.info("Storage Mode: " + storageMode);
    log.info("Rest API Port: " + restApiPort);
  }
}
