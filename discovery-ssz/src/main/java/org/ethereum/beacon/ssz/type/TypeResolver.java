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

package org.ethereum.beacon.ssz.type;

import org.ethereum.beacon.ssz.access.SSZField;

/** Creates the SSZ type given the Java type descriptor */
public interface TypeResolver {

  /**
   * Resolves SSZ type from the Class representation.
   *
   * <p>Note that raw class may not be sufficient to resolve the type. If for example the {@link
   * java.util.List} class is trying to be resolved it will fail since element information from the
   * parametrized type argument is not available. For this case the {@link
   * #resolveSSZType(SSZField)} variant should be used where {@link SSZField#getParametrizedType()}
   * would contain element type info
   *
   * <p>Another case is where <code>int</code> value should be treated as SSZ <code>uint64</code>
   * type (Java <code>int</code> is treated as SSZ <code>uint32</code> by default). In that case the
   * {@link #resolveSSZType(SSZField)} variant should be used as well where {@link
   * SSZField#getExtraSize()} would contain concrete SSZ basic type
   */
  default SSZType resolveSSZType(Class<?> clazz) {
    return resolveSSZType(new SSZField(clazz));
  }

  /**
   * Resolves SSZ type by its Java type descriptor which contains extra type information besides the
   * <code>Class</code> to properly identify the correct SSZ type and appropriate Java instance
   * accessor
   */
  SSZType resolveSSZType(SSZField descriptor);
}
