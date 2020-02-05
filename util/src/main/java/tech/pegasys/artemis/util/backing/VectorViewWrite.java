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

package tech.pegasys.artemis.util.backing;

public interface VectorViewWrite<W extends ViewWrite, R extends ViewRead>
    extends CompositeViewWrite<W, R>, VectorViewRead<W> {

  @Override
  default VectorViewRead<W> commitChanges() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] args) throws Exception {
    VectorViewRead<ContainerViewRead<ViewRead>> v1 = null;
    ListViewWrite<? extends ViewWrite, ContainerViewRead<ViewRead>> v2 = v1
        .createWritableCopy();
    ListViewRead<ContainerViewRead<ViewRead>> v3 = v2.commitChanges();
  }
}
