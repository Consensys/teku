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

package tech.pegasys.artemis.util.alogger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ALogger {

  private Logger logger;

  public ALogger() {
    this.logger = LogManager.getLogger();
  }

  public ALogger(String className) {
    this.logger = LogManager.getLogger(className);
  }

  public void log(Level level, String message) {
    this.logger.log(level, message);
  }

  public void log(Level info, String message, boolean printEnabled) {
    if (printEnabled) {
      this.logger.log(info, message);
    }
  }
}
