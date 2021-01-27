/*
 * Copyright (c) 2019 Works Applications Co., Ltd.
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

package com.worksap.nlp.elasticsearch.plugins.chikkar;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntryManagerTest {
    static List<String> vocab = new ArrayList<>();
    static EntryManager manager = new EntryManager();

    @BeforeClass
    public static void loadVocabulary() {
        for (char alphabet = 'a'; alphabet <= 'z'; ++alphabet) {
            vocab.add(Character.toString(alphabet));
        }

        for (String word : vocab) {
            manager.insertEntry(word, new Entry());
        }
    }

    @Test
    public void testRetrieveEntry() {
        List<Integer> found;
        for (int i = 0; i < vocab.size(); ++i) {
            String word = vocab.get(i);
            found = manager.retrieveEntry(word);
            assertEquals(1, found.size());
            assertEquals(i, found.get(0).longValue());
        }
    }

    @Test
    public void testGetWordsFromId() {
        int idx = 0;
        for (char ch = 'a'; ch <= 'z'; ++ch) {
            List<Integer> indices = new ArrayList<>();
            List<String> expected = new ArrayList<>();
            indices.add(idx++);
            expected.add(Character.toString(ch));
            assertEquals(expected, manager.getWordsFromId(indices));
        }
    }

    @Test
    public void testRetrieveNonExistEntry() {
        List<Integer> found = manager.retrieveEntry("non-exist");
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindLongestWordWithRelation() {
        EntryManager manager = new EntryManager();
        StringBuilder sb = new StringBuilder();
        for (char ch = 'a'; ch <= 'h'; ++ch) {
            sb.append(ch);
            manager.insertEntry(sb.toString(), new Entry());
        }

        sb.delete(0, sb.length());
        sb.insert(0, "abcabcdefgh");

        assertEquals("abc", manager.findLongestWordWithRelation(sb.toString(), 0, sb.length()));
        assertEquals("", manager.findLongestWordWithRelation(sb.toString(), 1, sb.length()));
        assertEquals("abcdefgh", manager.findLongestWordWithRelation(sb.toString(), 3, sb.length()));
        assertEquals("abcde", manager.findLongestWordWithRelation(sb.toString(), 3, 8));
        assertEquals("", manager.findLongestWordWithRelation("ersdfaswear", 0, 11));
    }
}
