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

package tech.pegasys.artemis.datastructures.operations;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;

public class CasperSlashing {

  private SlashableVoteData votes_1;
  private SlashableVoteData votes_2;

  public CasperSlashing(SlashableVoteData votes_1, SlashableVoteData votes_2) {
    this.votes_1 = votes_1;
    this.votes_2 = votes_2;
  }

  public static CasperSlashing fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new CasperSlashing(
                SlashableVoteData.fromBytes(reader.readBytes()),
                SlashableVoteData.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(votes_1.toBytes());
          writer.writeBytes(votes_2.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(votes_1, votes_2);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof CasperSlashing)) {
      return false;
    }

    CasperSlashing other = (CasperSlashing) obj;
    return Objects.equals(this.getVotes_1(), other.getVotes_1())
        && Objects.equals(this.getVotes_2(), other.getVotes_2());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SlashableVoteData getVotes_1() {
    return votes_1;
  }

  public void setVotes_1(SlashableVoteData votes_1) {
    this.votes_1 = votes_1;
  }

  public SlashableVoteData getVotes_2() {
    return votes_2;
  }

  public void setVotes_2(SlashableVoteData votes_2) {
    this.votes_2 = votes_2;
  }
}
