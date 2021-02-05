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

package tech.pegasys.teku.spec.containers.state;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszField;

public class BeaconStateSchema extends SszContainerSchema<BeaconState> {

  BeaconStateSchema(final List<NamedSchema<?>> fieldSchemas) {
    super("BeaconState", fieldSchemas);
  }

  public static BeaconStateSchema create(List<SszField> fields) {
    final List<NamedSchema<?>> namedFields =
        fields.stream()
            .map(f -> namedSchema(f.getName(), f.getSchema().get()))
            .collect(Collectors.toList());
    return new BeaconStateSchema(namedFields);
  }

  @Override
  public BeaconState createFromBackingNode(final TreeNode node) {
    return new BeaconStateImpl(this, node);
  }
}
