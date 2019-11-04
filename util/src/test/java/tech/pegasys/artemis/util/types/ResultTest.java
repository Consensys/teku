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

package tech.pegasys.artemis.util.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ResultTest {
  @SuppressWarnings("unchecked")
  private final Consumer<String> successConsumer = Mockito.mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final Consumer<String> errorConsumer = Mockito.mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final Function<String, Boolean> successFunction = Mockito.mock(Function.class);

  @SuppressWarnings("unchecked")
  private final Function<String, Boolean> errorFunction = Mockito.mock(Function.class);

  @BeforeEach
  public void setUp() {
    when(successFunction.apply(anyString())).thenReturn(true);
    when(errorFunction.apply(anyString())).thenReturn(false);
  }

  @Test
  public void eitherForSuccess() {
    final Result<String, String> result = Result.success("Yay");
    result.either(successConsumer, errorConsumer);
    verify(successConsumer).accept("Yay");
    verifyZeroInteractions(errorConsumer);
  }

  @Test
  public void eitherForFailure() {
    final Result<String, String> result = Result.error("Nope");
    result.either(successConsumer, errorConsumer);
    verify(errorConsumer).accept("Nope");
    verifyZeroInteractions(successConsumer);
  }

  @Test
  public void mapEitherForSuccess() {
    final Result<String, String> result = Result.success("Yay");
    assertThat(result.mapEither(successFunction, errorFunction)).isTrue();
    verify(successFunction).apply("Yay");
    verifyZeroInteractions(errorConsumer);
  }

  @Test
  public void mapEitherForFailure() {
    final Result<String, String> result = Result.error("Nope");
    assertThat(result.mapEither(successFunction, errorFunction)).isFalse();
    verify(errorFunction).apply("Nope");
    verifyZeroInteractions(successConsumer);
  }
}
