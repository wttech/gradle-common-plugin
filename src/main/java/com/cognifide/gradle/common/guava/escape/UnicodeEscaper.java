/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cognifide.gradle.common.guava.escape;

public abstract class UnicodeEscaper extends Escaper {
  private static final int DEST_PAD = 32;

  protected UnicodeEscaper() {}

  protected abstract char[] escape(int cp);

  protected int nextEscapeIndex(CharSequence csq, int start, int end) {
    int index = start;
    while (index < end) {
      int cp = codePointAt(csq, index, end);
      if (cp < 0 || escape(cp) != null) {
        break;
      }
      index += Character.isSupplementaryCodePoint(cp) ? 2 : 1;
    }
    return index;
  }


  @Override
  public String escape(String string) {
    int end = string.length();
    int index = nextEscapeIndex(string, 0, end);
    return index == end ? string : escapeSlow(string, index);
  }

  protected final String escapeSlow(String s, int index) {
    int end = s.length();

    // Get a destination buffer and setup some loop variables.
    char[] dest = Platform.charBufferFromThreadLocal();
    int destIndex = 0;
    int unescapedChunkStart = 0;

    while (index < end) {
      int cp = codePointAt(s, index, end);
      if (cp < 0) {
        throw new IllegalArgumentException(
            "Trailing high surrogate at end of input");
      }
      char[] escaped = escape(cp);
      int nextIndex = index + (Character.isSupplementaryCodePoint(cp) ? 2 : 1);
      if (escaped != null) {
        int charsSkipped = index - unescapedChunkStart;

        int sizeNeeded = destIndex + charsSkipped + escaped.length;
        if (dest.length < sizeNeeded) {
          int destLength = sizeNeeded + (end - index) + DEST_PAD;
          dest = growBuffer(dest, destIndex, destLength);
        }
        if (charsSkipped > 0) {
          s.getChars(unescapedChunkStart, index, dest, destIndex);
          destIndex += charsSkipped;
        }
        if (escaped.length > 0) {
          System.arraycopy(escaped, 0, dest, destIndex, escaped.length);
          destIndex += escaped.length;
        }
        unescapedChunkStart = nextIndex;
      }
      index = nextEscapeIndex(s, nextIndex, end);
    }

    int charsSkipped = end - unescapedChunkStart;
    if (charsSkipped > 0) {
      int endIndex = destIndex + charsSkipped;
      if (dest.length < endIndex) {
        dest = growBuffer(dest, destIndex, endIndex);
      }
      s.getChars(unescapedChunkStart, end, dest, destIndex);
      destIndex = endIndex;
    }
    return new String(dest, 0, destIndex);
  }

  protected static int codePointAt(CharSequence seq, int index, int end) {
    if (index < end) {
      char c1 = seq.charAt(index++);
      if (c1 < Character.MIN_HIGH_SURROGATE ||
          c1 > Character.MAX_LOW_SURROGATE) {
        return c1;
      } else if (c1 <= Character.MAX_HIGH_SURROGATE) {
        if (index == end) {
          return -c1;
        }
        char c2 = seq.charAt(index);
        if (Character.isLowSurrogate(c2)) {
          return Character.toCodePoint(c1, c2);
        }
        throw new IllegalArgumentException(
            "Expected low surrogate but got char '" + c2 +
            "' with value " + (int) c2 + " at index " + index +
            " in '" + seq + "'");
      } else {
        throw new IllegalArgumentException(
            "Unexpected low surrogate character '" + c1 +
            "' with value " + (int) c1 + " at index " + (index - 1) +
            " in '" + seq + "'");
      }
    }
    throw new IndexOutOfBoundsException("Index exceeds specified range");
  }

  private static char[] growBuffer(char[] dest, int index, int size) {
    char[] copy = new char[size];
    if (index > 0) {
      System.arraycopy(dest, 0, copy, 0, index);
    }
    return copy;
  }
}
