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

package org.ethereum.beacon.ssz.type.list;

import java.util.Optional;
import org.ethereum.beacon.ssz.ExternalVarResolver;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.TypeParameterResolver;

public class VectorSizeAnnotationResolver implements TypeParameterResolver<Number> {

  private final ExternalVarResolver externalVarResolver;

  public VectorSizeAnnotationResolver(ExternalVarResolver externalVarResolver) {
    this.externalVarResolver = externalVarResolver;
  }

  @Override
  public Optional<Number> resolveTypeParameter(SSZField descriptor) {
    if (descriptor.getFieldAnnotation() != null) {
      int vectorSize = descriptor.getFieldAnnotation().vectorLength();
      if (vectorSize > 0) {
        return Optional.of(vectorSize);
      }
      String vectorSizeVar = descriptor.getFieldAnnotation().vectorLengthVar();
      if (!vectorSizeVar.isEmpty()) {
        return Optional.of(externalVarResolver.resolveOrThrow(vectorSizeVar, Number.class));
      }
    }

    return Optional.empty();
  }
}
