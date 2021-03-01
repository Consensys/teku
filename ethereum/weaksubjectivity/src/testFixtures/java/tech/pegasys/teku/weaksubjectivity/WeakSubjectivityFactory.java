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

package tech.pegasys.teku.weaksubjectivity;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityFactory {

  private static final Spec SPEC = SpecFactory.createMinimal();
  private static final WeakSubjectivityConfig wsConfig =
      WeakSubjectivityConfig.builder().specProvider(SPEC).build();

  public static WeakSubjectivityValidator lenientValidator() {
    return WeakSubjectivityValidator.lenient(wsConfig);
  }
}
