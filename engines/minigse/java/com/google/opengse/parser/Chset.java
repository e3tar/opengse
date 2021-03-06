// Copyright 2007 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.opengse.parser;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * The {@code Chset} (character set) parser matches the current character
 * in the parse buffer against an arbitrary character set. The character set is
 * represented as a sorted array of ranges for which a match should be
 * successful. Matching takes O(log nranges) time. There are predefined
 * character sets for matching any character ({@code ANYCHAR}), no
 * characters ({@code NOTHING}) and some standard 7-bit ASCII ranges
 * ({@code ALNUM}, {@code ALPHA}, {@code DIGIT},
 * {@code XDIGIT}, {@code LOWER}, {@code UPPER},
 * {@code WHITESPACE}), and {@code ASCII}.
 *
 * Note that the character set parser only matches a single character of the
 * parse buffer. The {@code Sequence} or }Repeat} parsers need
 * to be used to match more than one character.
 *
 * The following matches vowels and digits:
 * <pre>   {@code
 *   Parser p = new Chset("uoiea0-9");
 *   p.parse("a") -> matches "a"
 *   p.parse("3") -> matches "3"
 *   p.parse("b") -> no match
 * } </pre>
 *
 * @see Parser
 * @author Peter Mattis
 */
public final class Chset extends Parser<Object> implements Cloneable {

  protected static final char MIN_CHAR = 0;
  protected static final char MAX_CHAR = 65535;

  private static final char MAX_ASCII_CHAR = 127;

  public static final Chset ANYCHAR = new Chset(MIN_CHAR, MAX_CHAR);
  public static final Chset NOTHING = new Chset();
  public static final Chset ALNUM = new Chset("a-zA-Z0-9");
  public static final Chset ALPHA = new Chset("a-zA-Z");
  public static final Chset DIGIT = new Chset("0-9");
  public static final Chset XDIGIT = new Chset("0-9a-fA-F");
  public static final Chset LOWER = new Chset("a-z");
  public static final Chset UPPER = new Chset("A-Z");
  public static final Chset WHITESPACE = new Chset(" \t\r\n\f");
  public static final Chset ASCII = new Chset(MIN_CHAR, MAX_ASCII_CHAR);

  private final ArrayList<Range> ranges = new ArrayList<Range>();

  /**
   * A secondary representation for ASCII members of the character set.
   * Maintaining this bitmap allows us to check ASCII characters for set
   * membership quickly.
   */
  private final BitSet asciiSet = new BitSet(MAX_ASCII_CHAR + 1);

  /**
   * Class constructor for an empty character set.
   */
  public Chset() {
    super();
  }

  /**
   * Class constructor for a character literal.
   *
   * @param ch The character literal for this character set to match against.
   */
  public Chset(char ch) {
    this(ch, ch);
  }

  /**
   * Class constructor for a single character range. The range is inclusive:
   * all character including {@code min} and {@code max} match.
   *
   * @param min The beginning of the character range.
   * @param max The end of the character range.
   */
  public Chset(char min, char max) {
    super();
    ranges.add(new Range(min, max));
    refreshAsciiSet();
  }

  /**
   * Class constructor that initializes a {@code Chset} from a string
   * specification.
   *
   * @param spec The string specification to initialize the {@code Chset}
   * from.
   */
  public Chset(String spec) {
    for (int i = 0; i < spec.length();) {
      final char s = spec.charAt(i);
      if ((i + 1) < spec.length()) {
        final char n = spec.charAt(i + 1);
        if (n == '-') {
          if ((i + 2) < spec.length()) {
            final char e = spec.charAt(i + 2);
            set(new Range(s, e));
            i += 3;
            continue;
          } else {
            set(new Range(s, s));
            set(new Range('-', '-'));
            break;
          }
        }
      }
      set(new Range(s, s));
      i += 1;
    }
  }

  /**
   * Returns a clone character set of {@code this}.
   */
  @Override
  public Chset clone() {
    Chset n = new Chset();
    for (Range r : ranges) {
      n.ranges.add(new Range(r.first, r.last));
    }
    return n;
  }

  /**
   * Matches {@code buf[start]} against the character set.
   *
   * @see Parser#parse
   */
  @Override
  public int parse(char[] buf, int start, int end, Object data) {
    if ((start < end) && checkInCharacterSet(buf[start])) {
      return 1;
    }
    return NO_MATCH;
  }

  /**
   * Tests to see if a single character matches the character set.
   *
   * @param ch The character to test.
   */
  public boolean checkInCharacterSet(char ch) {
    if (ch <= MAX_ASCII_CHAR) {
      return asciiSet.get(ch);
    }
    return checkInRanges(ch);
  }

  /**
   * Tests to see if a single character matches the character set, but only
   * looks at the ranges representation.
   *
   * @param ch The character to test.
   */
  protected boolean checkInRanges(char ch) {
    int rangeSize = ranges.size();

    if (rangeSize == 0) {
      return false;
    } else if (rangeSize == 1) {
      // Optimization for a common simple case -- we don't need to do a find().
      return ranges.get(0).includes(ch);
    } else {
      int pos = find(ch);

      // We need to test both the range at the position the character would be
      // inserted at and the preceding range due to the semantics of find().
      // For example, if the Chset contains a single range of [10-19], then
      // find() will return 1 for the range [11-11] and we'll want to test
      // against 'pos - 1'.
      if ((pos != rangeSize) && ranges.get(pos).includes(ch)) {
        return true;
      }
      if ((pos != 0) && ranges.get(pos - 1).includes(ch)) {
        return true;
      }
      return false;
    }
  }

  /**
   * @see #set(Range)
   */
  protected void set(char min, char max) {
    set(new Range(min, max));
  }

  /**
   * Sets the specified range of characters in the character set so that
   * subsequent calls to {@code test} for characters within the range will
   * return {@code true}.
   *
   * @see #union
   */
  private void set(Range r) {
    if (ranges.isEmpty()) {
      ranges.add(r);
      refreshAsciiSet();
      return;
    }

    int pos = find(r.first);
    if (((pos != ranges.size()) && ranges.get(pos).includes(r)) ||
        ((pos != 0) && ranges.get(pos - 1).includes(r))) {
      return;
    }
    if ((pos != 0) && ranges.get(pos - 1).mergeable(r)) {
      merge(pos - 1, r);
    } else if ((pos != ranges.size()) && ranges.get(pos).mergeable(r)) {
      merge(pos, r);
    } else {
      ranges.add(pos, r);
    }
    refreshAsciiSet();
  }

  /**
   * @see #clear(Range)
   */
  protected void clear(char min, char max) {
    clear(new Range(min, max));
  }

  /**
   * Clears the specified range of characters from the character set so that
   * subsequent calls to {@code test} for characters within the range will
   * return {@code false}.
   *
   * @see #difference(Chset, Chset)
   */
  private void clear(Range r) {
    if (ranges.isEmpty()) {
      return;
    }

    int pos = find(r.first);
    if (pos > 0) {
      Range prev = ranges.get(pos - 1);
      if (prev.includes(r.first)) {
        if (prev.last > r.last) {
          Range n = new Range(r.last + 1, prev.last);
          prev.last = r.first - 1;
          ranges.add(pos, n);
          refreshAsciiSet();
          return;
        } else {
          prev.last = r.first - 1;
        }
      }
    }

    while ((pos < ranges.size()) && r.includes(ranges.get(pos))) {
      ranges.remove(pos);
    }
    if ((pos < ranges.size()) && ranges.get(pos).includes(r.last)) {
      ranges.get(pos).first = r.last + 1;
    }

    refreshAsciiSet();
  }

  /**
   * Reconstructs the BitSet representation of the ASCII characters in the
   * set, so that it matches what's stored in ranges.
   */
  private void refreshAsciiSet() {
    asciiSet.clear();
    for (char ch = MIN_CHAR; ch <= MAX_ASCII_CHAR; ch++) {
      if (checkInRanges(ch)) {
        asciiSet.set(ch);
      }
    }
  }

  /**
   * Returns the size of the range array.
   */
  protected int size() {
    return ranges.size();
  }

  /**
   * Find the position in the range array for which the beginning of the
   * specified range is greater than or equal to the range at that position. In
   * other words, it returns the insertion point for the specified range.
   *
   * @param first The start of the range to find the insertion point for.
   *
   * @see #checkInCharacterSet(char)
   * @see #set(char, char)
   * @see #clear(char, char)
   * @see java.util.Arrays#binarySearch(char[], char)
   */
  private int find(int first) {
    int s = 0;
    int e = ranges.size() - 1;

    while (s <= e) {
      int m = (s + e) / 2; // equivalent to: m = s + (e - s) / 2;
      Range r = ranges.get(m);

      if (r.first < first) {
        s = m + 1;
      } else if (r.first > first) {
        e = m - 1;
      } else {
        return m;
      }
    }

    return s;
  }

  /**
   * Merge the specified range with the range at the specified position in the
   * range array. After performing the merge operation, we iterate down the
   * range array and continue merging any newly mergeable ranges. The specified
   * range and the range at the specified position in the range array must be
   * mergeable.
   *
   * @see #set(Range)
   * @see #clear(Range)
   */
  private void merge(int pos, Range r) {
    Range t = ranges.get(pos);
    t.merge(r);

    pos += 1;
    while ((pos < ranges.size()) && t.mergeable(ranges.get(pos))) {
      t.merge(ranges.get(pos));
      ranges.remove(pos);
    }
  }

  /**
   * Creates a new character set which matches a character if that character
   * does not match the {@code subject} character set. This operation is
   * implemented by taking the difference of the {@code ANYCHAR} character
   * set and the {@code subject} character set.
   * <pre>
   *      ~subject --> anychar - subject
   * </pre>
   *
   * @param subject The source character set.
   */
  public static Chset not(Chset subject) {
    return difference(ANYCHAR, subject);
  }

  /**
   * Creates a new character set which matches a character if that character
   * matches either the {@code left} or {@code right} character sets.
   *
   *     left | right
   *
   *
   * @param left The left source character set.
   *
   * @param right The right source character set.
   */
  public static Chset union(Chset left, Chset right) {
    Chset n = left.clone();
    for (Range r : right.ranges) {
      n.set(r);
    }
    return n;
  }

  /**
   * Creates a new character set which matches a character if that character
   * matches the {@code left} character set but does not match the
   * {@code right} character set.
   *
   *     left - right
   *
   * @param left The left source character set.
   *
   * @param right The right source character set.
   */
  public static Chset difference(Chset left, Chset right) {
    Chset n = left.clone();
    for (Range r : right.ranges) {
      n.clear(r);
    }
    return n;
  }

  /**
   * Creates a new character set which matches a character if that character
   * matches both the {@code left} and {@code right} character sets.
   *
   *     left & right --> left - ~right
   *
   * @param left The left source character set.
   *
   * @param right The right source character set.
   */
  public static Chset intersection(Chset left, Chset right) {
    return difference(left, not(right));
  }

  /**
   * Creates a new character set which matches a character if that character
   * matches the {@code left} character set or the {@code right}
   * character set, but not both.
   *
   *     left ^ right --> (left - right) | (right - left)
   *
   * @param left The left source character set.
   *
   * @param right The right source character set.
   */
  public static Chset xor(Chset left, Chset right) {
    return union(difference(left, right), difference(right, left));
  }

  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < ranges.size(); i++) {
      Range r = ranges.get(i);
      if (i > 0) {
        buf.append(" ");
      }
      buf.append(r.first);
      buf.append("-");
      buf.append(r.last);
    }
    return buf.toString();
  }


  /**
   * The {@code Range} class represents a range from
   * {@code [first,last]}. It is used by the {@code Chset} class to
   * implement character sets for large alphabets where a bitmap based approach
   * would be too expensive in terms of memory. The {@code first} and
   * {@code last} member variables are specified as integers to avoid
   * casting that would be necessary if they were specified as characters due to
   * java's type promotion rules.
   *
   * @author Peter Mattis
   */
  static class Range {
    int first;
    int last;

    /**
     * Class constructor.
     *
     * @param first The beginning of the range.
     *
     * @param last The end of the range.
     */
    Range(int first, int last) {
      if (first > last) {
        throw new IllegalArgumentException("descending ranges not supported: " +
                                           first + "-" + last);
      }

      this.first = first;
      this.last = last;
    }

    /**
     * Tests whether the specified character lies within the target range.
     *
     * @param ch The character to test for inclusion.
     *
     * @see #checkInCharacterSet(char)
     * @see #set(char, char)
     * @see #clear(char, char)
     */
    boolean includes(int ch) {
      return (first <= ch) && (ch <= last);
    }

    /**
     * Tests whether the specified range lies entirely within the target range.
     *
     * @param r The range to test for inclusion.
     *
     * @see #set(Range)
     * @see #clear(char, char)
     */
    boolean includes(Range r) {
      return (first <= r.first) && (r.last <= last);
    }

    /**
     * Tests whether the specified range is mergeable with the target range. Two
     * ranges are mergeable if they can be replaced with a single range that
     * spans exactly the same range of values.
     *
     * @param r The range to test for mergeability with.
     *
     * @see #set(Range)
     */
    boolean mergeable(Range r) {
      /*
       * A range is mergeable if there are no gaps between the ranges.  If
       * there is a gap, then it will be obvious as the difference in the
       * extremes will be greater than the sum of the ranges.
       */
      return (1 + Math.max(last, r.last) - Math.min(first, r.first)) <=
        ((1 + r.last - r.first) + (1 + last - first));
    }

    /**
     * Merges the specified range with the target range. This function is simple
     * minded and will produce unexpected results if the ranges being merged are
     * not {@link #mergeable(Range)}.
     *
     * @param r The range to merge with.
     *
     * @see #set(char, char)
     * @see #merge(int, Range)
     */
    void merge(Range r) {
      first = Math.min(first, r.first);
      last = Math.max(last, r.last);
    }
  }
}

