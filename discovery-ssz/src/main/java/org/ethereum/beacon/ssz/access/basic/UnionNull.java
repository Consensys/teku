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

package org.ethereum.beacon.ssz.access.basic;

import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.visitor.SSZReader;
import tech.pegasys.artemis.util.collections.Union;

/** special dummy accessor for dedicated ReadUnion.Null class */
public class UnionNull implements SSZBasicAccessor {

  @Override
  public Set<String> getSupportedSSZTypes() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class> getSupportedClasses() {
    return Collections.singleton(Union.Null.class);
  }

  @Override
  public int getSize(SSZField field) {
    return 0;
  }

  @Override
  public void encode(Object value, SSZField field, OutputStream result) {}

  @Override
  public Object decode(SSZField field, SSZReader reader) {
    return null;
  }
}
