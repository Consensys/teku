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

package tech.pegasys.teku.core.lookup;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.util.async.SafeFuture;

public class BlockProviderFactory {

  public static BlockProvider fromList(final List<SignedBeaconBlock> blockAndStates) {
    final Map<Bytes32, SignedBeaconBlock> blocks =
        blockAndStates.stream()
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));

    return fromMap(blocks);
  }

  public static BlockProvider fromMap(final Map<Bytes32, SignedBeaconBlock> blockMap) {
    return (roots) ->
        SafeFuture.completedFuture(
            roots.stream()
                .map(blockMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity())));
  }
}
