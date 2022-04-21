/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi;

import static org.mockito.Mockito.mock;

import io.javalin.http.Context;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public abstract class AbstractMigratedBeaconHandlerTest {
  protected final Spec spec = TestSpecFactory.createMinimalPhase0();
  protected final SchemaDefinitionCache schemaDefinition = new SchemaDefinitionCache(spec);

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<SafeFuture<String>> args =
      ArgumentCaptor.forClass(SafeFuture.class);

  protected final Context context = mock(Context.class);

  protected final ValidatorDataProvider validatorDataProvider = mock(ValidatorDataProvider.class);
}
