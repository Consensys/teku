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

package tech.pegasys.artemis.beaconrestapi.schema;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonSerialize(as = ArrayList.class)
public class CommitteesResponse extends ArrayList<CommitteeAssignment> {

  private CommitteesResponse(List<CommitteeAssignment> committeeAssignments) {
    super(committeeAssignments);
  }

  public static CommitteesResponse fromList(
      List<tech.pegasys.artemis.datastructures.state.CommitteeAssignment> committeeAssignments) {
    List<CommitteeAssignment> input =
        committeeAssignments.stream().map(CommitteeAssignment::new).collect(Collectors.toList());
    return new CommitteesResponse(input);
  }
}
