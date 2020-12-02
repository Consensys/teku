package tech.pegasys.teku.ssz.backing.type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TypeHints {

  private static class TypeHint {}

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
