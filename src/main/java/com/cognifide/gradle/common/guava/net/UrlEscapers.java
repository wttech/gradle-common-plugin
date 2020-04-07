/*
 * Copyright (C) 2009 The Guava Authors
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

package com.cognifide.gradle.common.guava.net;

import com.cognifide.gradle.common.guava.escape.Escaper;

public final class UrlEscapers {
  private UrlEscapers() {}

  static final String URL_FORM_PARAMETER_OTHER_SAFE_CHARS = "-_.*";

  static final String URL_PATH_OTHER_SAFE_CHARS_LACKING_PLUS =
      "-._~" +        // Unreserved characters.
      "!$'()*,;&=" +  // The subdelim characters (excluding '+').
      "@:";           // The gendelim characters permitted in paths.


  public static Escaper urlFormParameterEscaper() {
    return URL_FORM_PARAMETER_ESCAPER;
  }

  private static final Escaper URL_FORM_PARAMETER_ESCAPER =
      new PercentEscaper(URL_FORM_PARAMETER_OTHER_SAFE_CHARS, true);


  public static Escaper urlPathSegmentEscaper() {
    return URL_PATH_SEGMENT_ESCAPER;
  }

  private static final Escaper URL_PATH_SEGMENT_ESCAPER =
      new PercentEscaper(URL_PATH_OTHER_SAFE_CHARS_LACKING_PLUS + "+", false);

  public static Escaper urlFragmentEscaper() {
    return URL_FRAGMENT_ESCAPER;
  }

  private static final Escaper URL_FRAGMENT_ESCAPER =
      new PercentEscaper(URL_PATH_OTHER_SAFE_CHARS_LACKING_PLUS + "+/?", false);
}
