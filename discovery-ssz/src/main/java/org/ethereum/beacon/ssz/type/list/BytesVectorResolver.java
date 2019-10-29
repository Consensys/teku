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
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.type.TypeParameterResolver;
import tech.pegasys.artemis.util.bytes.Bytes1;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;

/**
 * Resolves {@link Bytes1}, {@link Bytes4}, {@link Bytes32}, {@link Bytes48}, {@link Bytes96} using
 * type or annotation
 */
public class BytesVectorResolver implements TypeParameterResolver<Number> {
  @Override
  public Optional<Number> resolveTypeParameter(SSZField descriptor) {

    // By class
    if (Bytes1.class.isAssignableFrom(descriptor.getRawClass())) {
      return Optional.of(1);
    }
    if (Bytes4.class.isAssignableFrom(descriptor.getRawClass())) {
      return Optional.of(4);
    }
    if (Bytes32.class.isAssignableFrom(descriptor.getRawClass())) {
      return Optional.of(32);
    }
    if (Bytes48.class.isAssignableFrom(descriptor.getRawClass())) {
      return Optional.of(48);
    }
    if (Bytes96.class.isAssignableFrom(descriptor.getRawClass())) {
      return Optional.of(96);
    }

    // By annotation
    SSZSerializable annotation = descriptor.getRawClass().getAnnotation(SSZSerializable.class);
    if (annotation == null || annotation.serializeAs() == void.class) {
      return Optional.empty();
    }
    if (Bytes1.class.isAssignableFrom(annotation.serializeAs())) {
      return Optional.of(1);
    }
    if (Bytes4.class.isAssignableFrom(annotation.serializeAs())) {
      return Optional.of(4);
    }
    if (Bytes32.class.isAssignableFrom(annotation.serializeAs())) {
      return Optional.of(32);
    }
    if (Bytes48.class.isAssignableFrom(annotation.serializeAs())) {
      return Optional.of(48);
    }
    if (Bytes96.class.isAssignableFrom(annotation.serializeAs())) {
      return Optional.of(96);
    }

    return Optional.empty();
  }
}
