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

package tech.pegasys.teku.cli.deposit;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepositGenerateCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};
  private GenerateParams generateParams;
  private GenerateAction generateAction;

  @BeforeEach
  void setUp() {
    generateParams = mock(GenerateParams.class);
    generateAction = mock(GenerateAction.class);
    when(generateParams.createGenerateAction(anyBoolean())).thenReturn(generateAction);
  }

  @Test
  public void generatesKeysWithDisplayConfirmation() {
    final DepositGenerateCommand depositGenerateCommand =
        new DepositGenerateCommand(shutdownFunction, generateParams, true);

    depositGenerateCommand.run();
    verify(generateParams).createGenerateAction(true);
    verify(generateAction).generateKeys();
  }

  @Test
  public void generatesKeysWithoutDisplayConfirmation() {
    final DepositGenerateCommand depositGenerateCommand =
        new DepositGenerateCommand(shutdownFunction, generateParams, false);

    depositGenerateCommand.run();
    verify(generateParams).createGenerateAction(false);
    verify(generateAction).generateKeys();
  }
}
