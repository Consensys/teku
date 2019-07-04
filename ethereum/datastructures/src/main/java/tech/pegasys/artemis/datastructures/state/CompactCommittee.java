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

package tech.pegasys.artemis.datastructures.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class CompactCommittee implements Copyable<CompactCommittee> {

    private List<Bytes48> pubkeys; // Bounded by SLOTS_PER_HISTORICAL_ROOT
    private List<UnsignedLong> compact_validators; // Bounded by SLOTS_PER_HISTORICAL_ROOT

    public CompactCommittee(List<Bytes48> block_roots, List<UnsignedLong> state_roots) {
        this.pubkeys = block_roots;
        this.compact_validators = state_roots;
    }

    public CompactCommittee(CompactCommittee CompactCommittee) {
        this.pubkeys = copyBytesList(CompactCommittee.getBlockRoots(), new ArrayList<>());
        this.compact_validators = copyBytesList(CompactCommittee.getStateRoots(), new ArrayList<>());
    }

    // @todo
}
