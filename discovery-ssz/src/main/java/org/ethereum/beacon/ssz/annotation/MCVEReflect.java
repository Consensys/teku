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

package org.ethereum.beacon.ssz.annotation;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import org.javatuples.Pair;

/**
 * Searches an Annotation through a method predecessors (overridden or implemented)
 *
 * <p>Taken from here https://stackoverflow.com/a/49164791/9630725
 */
public final class MCVEReflect {
  private MCVEReflect() {}

  /**
   * Returns the 0th element of the list returned by {@code getAnnotations}, or {@code null} if the
   * list would be empty.
   *
   * @param  <A> the type of the annotation to find.
   * @param m the method to begin the search from.
   * @param t the type of the annotation to find.
   * @return the first annotation found of the specified type which is present on {@code m}, or
   *     present on any methods which {@code m} overrides.
   * @throws NullPointerException if any argument is {@code null}.
   * @see MCVEReflect#getAnnotations(Method, Class)
   */
  public static <A extends Annotation> Pair<A, Method> getAnnotation(Method m, Class<A> t) {
    List<Pair<A, Method>> list = getAnnotations(m, t);
    return list.isEmpty() ? null : list.get(0);
  }

  /**
   * Let {@code D} be the class or interface which declares the method {@code m}.
   *
   * <p>Returns a list of all of the annotations of the specified type which are either present on
   * {@code m}, or present on any methods declared by a supertype of {@code D} which {@code m}
   * overrides.
   *
   * <p>Annotations are listed in order of nearest proximity to {@code D}, that is, assuming {@code
   * D extends E} and {@code E extends F}, then the returned list would contain annotations in the
   * order of {@code [D, E, F]}. A bit more formally, if {@code Sn} is the nth superclass of {@code
   * D} (where {@code n} is an integer starting at 0), then the index of the annotation present on
   * {@code Sn.m} is {@code n+1}, assuming annotations are present on {@code m} for every class.
   *
   * <p>Annotations from methods declared by the superinterfaces of {@code D} appear <em>last</em>
   * in the list, in order of their declaration, recursively. For example, if {@code class D
   * implements X, Y} and {@code interface X extends Z}, then annotations will appear in the list in
   * the order of {@code [D, X, Z, Y]}.
   *
   * @param  <A> the type of the annotation to find.
   * @param m the method to begin the search from.
   * @param t the type of the annotation to find.
   * @return a list of all of the annotations of the specified type which are either present on
   *     {@code m}, or present on any methods which {@code m} overrides.
   * @throws NullPointerException if any argument is {@code null}.
   */
  public static <A extends Annotation> List<Pair<A, Method>> getAnnotations(Method m, Class<A> t) {
    List<Pair<A, Method>> list = new ArrayList<>();
    for (A a : m.getAnnotationsByType(t)) {
      list.add(Pair.with(a, m));
    }
    Class<?> decl = m.getDeclaringClass();

    for (Class<?> supr = decl; (supr = supr.getSuperclass()) != null; ) {
      addAnnotations(list, m, t, supr);
    }
    for (Class<?> face : getAllInterfaces(decl)) {
      addAnnotations(list, m, t, face);
    }

    return list;
  }

  private static Set<Class<?>> getAllInterfaces(Class<?> c) {
    Set<Class<?>> set = new LinkedHashSet<>();
    do {
      addAllInterfaces(set, c);
    } while ((c = c.getSuperclass()) != null);
    return set;
  }

  private static void addAllInterfaces(Set<Class<?>> set, Class<?> c) {
    for (Class<?> i : c.getInterfaces()) {
      if (set.add(i)) {
        addAllInterfaces(set, i);
      }
    }
  }

  private static <A extends Annotation> void addAnnotations(
      List<Pair<A, Method>> list, Method m, Class<A> t, Class<?> decl) {
    try {
      Method n = decl.getDeclaredMethod(m.getName(), m.getParameterTypes());
      if (overrides(m, n)) {
        for (A a : n.getAnnotationsByType(t)) {
          list.add(Pair.with(a, n));
        }
      }
    } catch (NoSuchMethodException x) {
    }
  }

  /**
   * @param a the method which may override {@code b}.
   * @param b the method which may be overridden by {@code a}.
   * @return {@code true} if {@code a} probably overrides {@code b} and {@code false} otherwise.
   * @throws NullPointerException if any argument is {@code null}.
   */
  public static boolean overrides(Method a, Method b) {
    if (!a.getName().equals(b.getName())) {
      return false;
    }
    Class<?> classA = a.getDeclaringClass();
    Class<?> classB = b.getDeclaringClass();
    if (classA.equals(classB)) {
      return false;
    }
    if (!classB.isAssignableFrom(classA)) {
      return false;
    }
    int modsA = a.getModifiers();
    int modsB = b.getModifiers();
    if (Modifier.isPrivate(modsA) || Modifier.isPrivate(modsB)) {
      return false;
    }
    if (Modifier.isStatic(modsA) || Modifier.isStatic(modsB)) {
      return false;
    }
    if (Modifier.isFinal(modsB)) {
      return false;
    }
    if (compareAccess(modsA, modsB) < 0) {
      return false;
    }
    if ((isPackageAccess(modsA) || isPackageAccess(modsB))
        && !Objects.equals(classA.getPackage(), classB.getPackage())) {
      return false;
    }
    if (!b.getReturnType().isAssignableFrom(a.getReturnType())) {
      return false;
    }
    Class<?>[] paramsA = a.getParameterTypes();
    Class<?>[] paramsB = b.getParameterTypes();
    if (paramsA.length != paramsB.length) {
      return false;
    }
    for (int i = 0; i < paramsA.length; ++i) {
      if (!paramsA[i].equals(paramsB[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean isPackageAccess(int mods) {
    return (mods & ACCESS_MODIFIERS) == 0;
  }

  private static final int ACCESS_MODIFIERS =
      Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
  private static final List<Integer> ACCESS_ORDER =
      Arrays.asList(Modifier.PRIVATE, 0, Modifier.PROTECTED, Modifier.PUBLIC);

  public static int compareAccess(int lhs, int rhs) {
    return Integer.compare(
        ACCESS_ORDER.indexOf(lhs & ACCESS_MODIFIERS), ACCESS_ORDER.indexOf(rhs & ACCESS_MODIFIERS));
  }
}
