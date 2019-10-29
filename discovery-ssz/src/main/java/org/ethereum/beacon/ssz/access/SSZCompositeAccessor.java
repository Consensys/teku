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

import org.ethereum.beacon.ssz.type.SSZType;

/**
 * Interface describes common functionality of {@link SSZContainerAccessor} and @{@link
 * SSZListAccessor}
 */
public interface SSZCompositeAccessor {

  /**
   * Common interface for building new List and Container instances The following steps are required
   * to create a new List or Container java representation with content: - create a {@link
   * CompositeInstanceBuilder} instance with {@link #createInstanceBuilder(SSZType)} - fill content
   * via {@link #setChild(int, Object)} calls - create a new instance with specified children with
   * {@link #build()} call
   */
  interface CompositeInstanceBuilder {

    void setChild(int idx, Object childValue);

    Object build();
  }

  /** Interface for accessing child values of a java object representing SSZ List or Container */
  interface CompositeInstanceAccessor {

    Object getChildValue(Object compositeInstance, int childIndex);

    int getChildrenCount(Object compositeInstance);
  }

  /** @return <code>true</code> if this accessor supports given type descriptor */
  boolean isSupported(SSZField field);

  /**
   * Creates an object which is responsible for filling and creating a new List or Container
   * instance
   *
   * @see CompositeInstanceBuilder
   */
  CompositeInstanceBuilder createInstanceBuilder(SSZType sszType);

  /**
   * Creates corresponding java object accessor
   *
   * @see CompositeInstanceAccessor
   */
  CompositeInstanceAccessor getInstanceAccessor(SSZField compositeDescriptor);
}
