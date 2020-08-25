/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.fuzz.input;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class BlockFuzzInput implements SimpleOffsetSerializable, SSZContainer {

  private BeaconStateImpl state;
  private SignedBeaconBlock signed_block;

  public BlockFuzzInput(final BeaconStateImpl state, final SignedBeaconBlock signed_block) {
    this.state = state;
    this.signed_block = signed_block;
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public BlockFuzzInput() {
    this(new BeaconStateImpl(), new SignedBeaconBlock(new BeaconBlock(), BLSSignature.empty()));
  }

  @Override
  public int getSSZFieldCount() {
    return 2;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // Because we know both fields are variable and registered, we can just serialize.
    return List.of(
        SimpleOffsetSerializer.serialize(state), SimpleOffsetSerializer.serialize(signed_block));
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SignedBeaconBlock getSigned_block() {
    return signed_block;
  }

  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BlockFuzzInput)) {
      return false;
    }
    final BlockFuzzInput that = (BlockFuzzInput) o;
    return Objects.equals(getState(), that.getState())
        && Objects.equals(getSigned_block(), that.getSigned_block());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getState(), getSigned_block());
  }
}
