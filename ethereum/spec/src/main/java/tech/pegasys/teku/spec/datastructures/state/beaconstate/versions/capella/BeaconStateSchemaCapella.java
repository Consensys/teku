/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;

public class BeaconStateSchemaCapella
    extends AbstractBeaconStateSchema<BeaconStateBellatrix, MutableBeaconStateBellatrix> {

  @VisibleForTesting
  BeaconStateSchemaCapella(final SpecConfig specConfig) {
    super("BeaconStateBellatrix", getUniqueFields(specConfig), specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    return BeaconStateSchemaBellatrix.getUniqueFields(specConfig);
  }

  @Override
  public MutableBeaconStateBellatrix createBuilder() {
    return new MutableBeaconStateCapellaImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateCapella createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateCapellaImpl createEmptyBeaconStateImpl() {
    return new BeaconStateCapellaImpl(this);
  }

  @Override
  public BeaconStateBellatrix createFromBackingNode(final TreeNode node) {
    return null;
  }
}
