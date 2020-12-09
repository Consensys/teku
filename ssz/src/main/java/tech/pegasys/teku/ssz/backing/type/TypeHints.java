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

package tech.pegasys.teku.ssz.backing.type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A set of hints for {@link ViewType} classes on strategies to use for optimizing memory or/and
 * performance.
 */
public class TypeHints {

  private static class TypeHint {}

  /**
   * Hint to use {@link tech.pegasys.teku.ssz.backing.tree.SszSuperNode} for lists/vectors to save
   * the memory when the list content is expected to be rarely updated
   *
   * <p>The <code>depth</code> parameter specifies the maximum number (<code>2 ^ depth</code>) of
   * list/vector elements a single node can contain. Increasing this parameter saves memory but
   * makes list/vector update and hashTreeRoot recalculation more CPU expensive
   */
  public static final class SszSuperNodeHint extends TypeHint {
    private final int depth;

    public SszSuperNodeHint(int depth) {
      this.depth = depth;
    }

    public int getDepth() {
      return depth;
    }
  }

  public static TypeHints of(TypeHint... hints) {
    return new TypeHints(Arrays.asList(hints));
  }

  public static TypeHints none() {
    return of();
  }

  public static TypeHints sszSuperNode(int superNodeDepth) {
    return of(new SszSuperNodeHint(superNodeDepth));
  }

  private final List<TypeHint> hints;

  private TypeHints(List<TypeHint> hints) {
    this.hints = hints;
  }

  @SuppressWarnings("unchecked")
  public <C extends TypeHint> Optional<C> getHint(Class<C> hintClass) {
    return (Optional<C>) hints.stream().filter(h -> h.getClass() == hintClass).findFirst();
  }
}
