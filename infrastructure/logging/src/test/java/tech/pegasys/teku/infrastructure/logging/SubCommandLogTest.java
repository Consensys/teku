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

package tech.pegasys.teku.infrastructure.logging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SubCommandLogTest {
  private static final SubCommandLogger log = SubCommandLogger.SUB_COMMAND_LOG;
  private static final Logger logger = mock(Logger.class);

  @BeforeAll
  public static void setup() {
    log.setLogger(logger);
  }

  @Test
  public void shouldOutputToLoggerIfSet() {
    final String infoMessage = "Info message";
    log.display(infoMessage);
    verify(logger, times(1)).info(infoMessage);
    final String errorMessage = "Error message";
    log.error(errorMessage);
    verify(logger, times(1)).error(errorMessage);
    final Exception exception = new RuntimeException("Ouch");
    log.error(errorMessage, exception);
    verify(logger, times(1)).error(errorMessage, exception);
  }
}
