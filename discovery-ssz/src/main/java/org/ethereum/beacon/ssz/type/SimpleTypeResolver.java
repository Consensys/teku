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

import java.util.Optional;
import org.ethereum.beacon.ssz.ExternalVarResolver;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.access.AccessorResolver;
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZContainerAccessor;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor;

public class SimpleTypeResolver implements TypeResolver {

  private final AccessorResolver accessorResolver;
  private final ListTypeResolver listTypeResolver;

  public SimpleTypeResolver(
      AccessorResolver accessorResolver, ExternalVarResolver externalVarResolver) {
    this.accessorResolver = accessorResolver;
    this.listTypeResolver = new ListTypeResolver(accessorResolver, this, externalVarResolver);
  }

  @Override
  public SSZType resolveSSZType(SSZField descriptor) {
    Optional<SSZBasicAccessor> codec = accessorResolver.resolveBasicAccessor(descriptor);
    if (codec.isPresent()) {
      return new SSZBasicType(descriptor, codec.get());
    }

    Optional<SSZUnionAccessor> unionAccessor = accessorResolver.resolveUnionAccessor(descriptor);
    if (unionAccessor.isPresent()) {
      return new SSZUnionType(unionAccessor.get(), descriptor, this);
    }

    SSZType listType = listTypeResolver.resolveSSZType(descriptor);
    if (listType != null) {
      return listType;
    }

    Optional<SSZContainerAccessor> containerAccessor =
        accessorResolver.resolveContainerAccessor(descriptor);
    if (containerAccessor.isPresent()) {
      return new SSZContainerType(this, descriptor, containerAccessor.get());
    }

    throw new SSZSchemeException("Couldn't resolve type for descriptor " + descriptor);
  }
}
