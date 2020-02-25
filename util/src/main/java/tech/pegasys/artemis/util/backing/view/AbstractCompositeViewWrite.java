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

package tech.pegasys.artemis.util.backing.view;

import java.util.function.Consumer;
import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;

public abstract class AbstractCompositeViewWrite<
        C extends AbstractCompositeViewWrite<C, R>, R extends ViewRead>
    implements CompositeViewWrite<R> {

  private Consumer<ViewWrite> invalidator;

  protected void invalidate() {
    if (invalidator != null) {
      invalidator.accept(this);
    }
  }

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    invalidator = listener;
  }

  @Override
  @SuppressWarnings("unchecked")
  public C createWritableCopy() {
    return (C) getType().createFromTreeNode(getBackingNode());
  }

  @Override
  @SuppressWarnings("unchecked")
  public C commitChanges() {
    return (C) getType().createFromTreeNode(getBackingNode());
  }
}
