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

public class TestEntryManager {
    static final int LIST_SIZE = 2;
    static String posList[] = { "1", "2" };
    static String pronunList[] = { "p", "r" };
    static String semanticTagList[] = { "tag1", "tag2" };
    static List<String> vocab = new ArrayList<>();
    static EntryManager manager = new EntryManager();

    @BeforeClass
    public static void loadVocabulary() {
        for (char alphabet = 'a'; alphabet <= 'z'; ++alphabet) {
            vocab.add(Character.toString(alphabet));
        }

        for (String word : vocab) {
            for (int i = 0; i < LIST_SIZE; ++i) {
                for (int j = 0; j < LIST_SIZE; ++j) {
                    for (int k = 0; k < LIST_SIZE; ++k) {
                        manager.insertEntry(word, new Entry(pronunList[i], posList[j], semanticTagList[k]));
                    }
                }
            }
        }
    }

    @Test
    public void testRetrieveEntryWithoutAdditionalInfo() {
        List<Integer> found = null;
        for (int i = 0; i < vocab.size(); ++i) {
            String word = vocab.get(i);
            found = manager.retrieveEntry(word, null, null, null);
            assertEquals(LIST_SIZE * LIST_SIZE * LIST_SIZE, found.size());
            for (int idx : found) {
                assertEquals(i, idx / (LIST_SIZE * LIST_SIZE * LIST_SIZE));
            }
        }
    }

    @Test
    public void testRetrieveEntryWithPOS() {
        List<Integer> found = null;
        for (int i = 0; i < vocab.size(); ++i) {
            String word = vocab.get(i);
            for (int j = 0; j < LIST_SIZE; ++j) {
                found = manager.retrieveEntry(word, posList[j], null, null);
                assertEquals(LIST_SIZE * LIST_SIZE, found.size());
                for (int idx : found) {
                    assertEquals(i, idx / (LIST_SIZE * LIST_SIZE * LIST_SIZE));
                }
            }
        }
    }

    @Test
    public void testRetrieveEntryWithPronunciation() {
        List<Integer> found = null;
        for (int i = 0; i < vocab.size(); ++i) {
            String word = vocab.get(i);
            for (int j = 0; j < LIST_SIZE; ++j) {
                found = manager.retrieveEntry(word, null, pronunList[j], null);
                assertEquals(LIST_SIZE * LIST_SIZE, found.size());
                for (int idx : found) {
                    assertEquals(i, idx / (LIST_SIZE * LIST_SIZE * LIST_SIZE));
                }
            }
        }
    }

    @Test
    public void testRetrieveEntryWithSemanticTag() {
        List<Integer> found = null;
        for (int i = 0; i < vocab.size(); ++i) {
            String word = vocab.get(i);
            for (int j = 0; j < LIST_SIZE; ++j) {
                found = manager.retrieveEntry(word, null, null, semanticTagList[j]);
                assertEquals(LIST_SIZE * LIST_SIZE, found.size());
                for (int idx : found) {
                    assertEquals(i, idx / (LIST_SIZE * LIST_SIZE * LIST_SIZE));
                }
            }
        }
    }

    @Test
    public void testRetrieveEntryWithFullInfo() {
        List<Integer> found = null;
        for (int k = 0; k < vocab.size(); ++k) {
            String word = vocab.get(k);
            for (int i = 0; i < LIST_SIZE; ++i) {
                for (int j = 0; j < LIST_SIZE; ++j) {
                    for (int m = 0; m < LIST_SIZE; ++m) {
                        found = manager.retrieveEntry(word, posList[i], pronunList[j], semanticTagList[m]);
                        assertEquals(1, found.size());
                        for (int idx : found) {
                            assertEquals(k, idx / (LIST_SIZE * LIST_SIZE * LIST_SIZE));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testGetWordsFromId() {
        int idx = 0;
        for (char ch = 'a'; ch <= 'z'; ++ch) {
            List<Integer> indices = new ArrayList<>();
            List<String> expected = new ArrayList<>();
            for (int i = 0; i < LIST_SIZE; ++i) {
                for (int j = 0; j < LIST_SIZE; ++j) {
                    for (int k = 0; k < LIST_SIZE; ++k) {
                        indices.add(idx++);
                        expected.add(Character.toString(ch));
                    }
                }
            }
            assertEquals(expected, manager.getWordsFromId(indices));
        }
    }

    @Test
    public void testRetrieveNonExistEntry() {
        List<Integer> found = manager.retrieveEntry("non-exist", null, null, null);
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindLongestWordWithRelation() {
        EntryManager manager = new EntryManager();
        StringBuilder sb = new StringBuilder();
        for (char ch = 'a'; ch <= 'h'; ++ch) {
            sb.append(ch);
            manager.insertEntry(sb.toString(), new Entry(null, null, null));
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
