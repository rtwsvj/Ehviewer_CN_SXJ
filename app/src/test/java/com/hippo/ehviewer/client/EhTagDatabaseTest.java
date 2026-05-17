/*
 * Copyright 2019 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhTagDatabaseTest {

  @Test
  public void readTheList() throws IOException {
    InputStream resource = EhTagDatabaseTest.class.getResourceAsStream("EhTagDatabaseTest");

    EhTagDatabase db;
    try (BufferedSource source = Okio.buffer(Okio.source(resource))) {
      db = new EhTagDatabase("EhTagDatabaseTest", source);
    }

    assertEquals("a", db.getTranslation("1"));
    assertEquals("ab", db.getTranslation("12"));
    assertEquals("abc", db.getTranslation("123"));
    assertEquals("abcd", db.getTranslation("1234"));
    assertEquals("1", db.getTranslation("a"));
    assertEquals("12", db.getTranslation("ab"));
    assertEquals("123", db.getTranslation("abc"));
    assertEquals("1234", db.getTranslation("abcd"));
    assertNull(db.getTranslation("21"));
  }

  @Test
  public void suggestUsesNamespaceBucketAndKeepsReturnShape() throws IOException {
    EhTagDatabase db = createDatabase(
        entry("f:apple", "女苹果"),
        entry("m:apple", "男苹果"),
        entry("f:banana", "女香蕉"));

    List<Pair<String, String>> suggestions = db.suggest("female:a");

    assertEquals(1, suggestions.size());
    assertEquals("女苹果", suggestions.get(0).first);
    assertEquals("female:apple", suggestions.get(0).second);
  }

  @Test
  public void suggestIsCaseInsensitiveAndPreservesOrderLimit() throws IOException {
    Entry[] entries = new Entry[45];
    for (int i = 0; i < entries.length; i++) {
      entries[i] = entry(String.format("Match%02d", i), "翻译" + i);
    }
    EhTagDatabase db = createDatabase(entries);

    List<Pair<String, String>> suggestions = db.suggest("match");

    assertEquals(40, suggestions.size());
    assertEquals("Match00", suggestions.get(0).second);
    assertEquals("Match39", suggestions.get(39).second);
  }

  private static EhTagDatabase createDatabase(Entry... entries) throws IOException {
    StringBuilder data = new StringBuilder();
    for (Entry entry : entries) {
      data.append(entry.english)
          .append('\r')
          .append(Base64.getEncoder().encodeToString(entry.chinese.getBytes(StandardCharsets.UTF_8)))
          .append('\n');
    }
    byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
    Buffer buffer = new Buffer();
    buffer.writeInt(bytes.length);
    buffer.write(bytes);
    return new EhTagDatabase("test", buffer);
  }

  private static Entry entry(String english, String chinese) {
    return new Entry(english, chinese);
  }

  private static class Entry {
    final String english;
    final String chinese;

    Entry(String english, String chinese) {
      this.english = english;
      this.chinese = chinese;
    }
  }
}
