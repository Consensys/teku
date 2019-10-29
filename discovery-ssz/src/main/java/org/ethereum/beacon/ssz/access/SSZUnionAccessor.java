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

import java.util.List;

/**
 * Handles ssz unions, is responsible of accessing its child value, its type index and new instance
 * creation
 */
public interface SSZUnionAccessor extends SSZCompositeAccessor {

  interface UnionInstanceAccessor extends CompositeInstanceAccessor {

    /** Returns Union children type descriptors */
    List<SSZField> getChildDescriptors();

    int getTypeIndex(Object unionInstance);

    @Override
    default int getChildrenCount(Object compositeInstance) {
      return 1;
    }
  }

  UnionInstanceAccessor getInstanceAccessor(SSZField containerDescriptor);
}
