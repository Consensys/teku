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

package tech.pegasys.artemis.statetransition.util;

import java.util.List;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.statetransition.BeaconState;

/** This class is a collection of tree hash root convenience methods */
public final class TreeHashUtil {

  /**
   * Calculate the hash tree root of the provided value
   *
   * @param value
   * @return
   */
  public static Bytes32 hash_tree_root(Bytes value) {
    return SSZ.hashTreeRoot(value);
  }

  /**
   * Calculate the hash tree root of the list of validators provided
   *
   * @param validators
   * @return
   */
  public static Bytes32 hash_tree_root(List<Validator> validators) {
    return hash_tree_root(
        SSZ.encode(
            writer -> {
              writer.writeBytesList(
                  validators.stream().map(item -> item.toBytes()).collect(Collectors.toList()));
            }));
  }

  /**
   * Calculate the hash tree root of the BeaconState provided
   *
   * @param state
   * @return
   */
  public static Bytes32 hash_tree_root(BeaconState state) {
    return hash_tree_root(state.toBytes());
  }
}
