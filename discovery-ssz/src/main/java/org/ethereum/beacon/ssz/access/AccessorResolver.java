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

package org.ethereum.beacon.ssz.access;

import java.util.Optional;

/**
 * Taking the type descriptor ({@link SSZField} instance) finds a suitable accessor which is capable
 * of handling java instances of this type.
 *
 * <p>Accessors are normally singleton instances which are registered at application startup or can
 * be specified via @{@link org.ethereum.beacon.ssz.annotation.SSZSerializable} annotation
 * attributes.
 *
 * <p>Accessors can be of 3 types (with respect to SSZ documentation types):
 *
 * <p>- list: handles collections of homogeneous objects (e.g. java array, {@link java.util.List},
 * etc) which includes both ssz types: vectors and lists List accessor is responsible of accessing
 * list elements, their type and new instance creation - container: handles containers (aka
 * structures or ordered heterogenous collection of values) Container accessor is responsible of
 * accessing its child values, their types and new instance creation - basic objects: handles basic
 * types (numeric types of different lengths). Basic accessor is capable of SSZ encoding and
 * decoding values
 */
public interface AccessorResolver {

  /**
   * Resolves basic accessor given the type descriptor.
   *
   * @return non-empty value if the given type is basic and suitable accessor was found <code>
   *     {@link Optional#empty()}</code> otherwise
   */
  Optional<SSZBasicAccessor> resolveBasicAccessor(SSZField field);

  /**
   * Resolves list accessor given the type descriptor.
   *
   * @return non-empty value if the given type is supported list type <code>{@link Optional#empty()}
   *     </code> otherwise
   */
  Optional<SSZListAccessor> resolveListAccessor(SSZField field);

  /**
   * Resolves container accessor given the type descriptor. Normally this method returns {@link
   * org.ethereum.beacon.ssz.access.container.SimpleContainerAccessor} if the given type corresponds
   * to a POJO class annotated with @{@link org.ethereum.beacon.ssz.annotation.SSZSerializable}
   *
   * @return non-empty value if the given type is supported container type <code>
   *     {@link Optional#empty()}</code> otherwise
   */
  Optional<SSZContainerAccessor> resolveContainerAccessor(SSZField field);

  /**
   * Resolves union accessor given the type descriptor.
   *
   * @return non-empty value if the given type is supported union type <code>
   *     {@link Optional#empty()}</code> otherwise
   */
  Optional<SSZUnionAccessor> resolveUnionAccessor(SSZField field);
}
