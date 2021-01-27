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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.trie4j.MapTrie;
import org.trie4j.patricia.MapPatriciaTrie;

class EntryManager implements Serializable {
    private MapTrie<List<Entry>> vocabulary;
    private List<String> id2word;

    public EntryManager() {
        vocabulary = new MapPatriciaTrie<>();
        id2word = new ArrayList<>();
    }

    /**
     * This method insert a new Entry associated with a String. The String can be
     * already existed one or a new String. Namely, in the Storage, mapping from
     * String to Entry is one-to-many mapping.
     * <p>
     * When a new Entry is inserted, its `id` is updated by the EntryManager,
     * depends on the number of existing entries in the system.
     *
     * @param input
     *            The String user want to store.
     * @param entry
     *            The associated Entry with the given String.
     * @return None.
     */
    void insertEntry(String input, Entry entry) {
        if (!vocabulary.contains(input)) {
            vocabulary.insert(input, new ArrayList<>());
        }
        entry.setId(id2word.size());
        vocabulary.get(input).add(entry);
        id2word.add(input);
    }

    List<Entry> getEntries(String input) {
        return vocabulary.get(input);
    }

    /**
     * This method retrieves all related `Entry` associated with the given `input`
     * String.
     * <p>
     * `input` is mandatory.
     * <p>
     * The returned result only contains the `Entry ID`s instead of `Entry`s stored
     * in a List.
     *
     * @param input
     *            The String that the user wants to query.
     *
     * @return {@code List<Integer>} Return a list of integers, containing the Entry
     *         ID instead of the whole Entry.
     */
    List<Integer> retrieveEntry(String input) {
        List<Entry> entries = vocabulary.get(input);
        if (entries == null)
            return Collections.emptyList();
        return entries.stream().map(Entry::getId).collect(Collectors.toList());
    }

    /**
     * This method returns the corresponding String from a given list of Entry ID.
     *
     * @param ids
     *            List of Entry ID for query.
     * @return {@code List<String>} List of Strings for the result.
     */
    List<String> getWordsFromId(List<Integer> ids) {
        return ids.stream().map(i -> id2word.get(i)).collect(Collectors.toList());
    }

    List<String> getWordsFromId(int id) {
        return getWordsFromId(Arrays.asList(id));
    }

    String findLongestWordWithRelation(String input, int start, int end) {
        StringBuilder rtn = new StringBuilder();
        int foundIdx = vocabulary.findLongestWord(input, start, end, rtn);
        if (foundIdx != start)
            return "";
        else
            return rtn.toString();
    }

    List<String> getSortedKeys() {
        return id2word.stream().distinct().sorted().collect(Collectors.toList());
    }
}
