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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;

import com.worksap.nlp.sudachi.Morpheme;

/**
 * The Chikkar class is the main interface provided by the Chikkar library.
 * Chikkar class has access to singleton DictionaryManager. Each Chikkar
 * instance maintains which dictionaries it uses and loads these instances from
 * the dictionary repository. Due to different set of active dictionaries, the
 * query result differs. For now we assume all dictionaries contain only
 * synonymous info.
 *
 * @author zhao_ya@worksap.co.jp &amp; liu_to@worksap.co.jp
 */

public class Chikkar {
    DictionaryManager dictMgn; // Assume only synonymous dictionary is in use.
    RelationManager relationMgn;

    private final Analyzer analyzer;
    private int dictId = 0;

    public static Chikkar clone(Chikkar obj) {
        Chikkar newObj = new Chikkar(obj.analyzer);
        newObj.dictId = obj.dictId;
        newObj.relationMgn = RelationManager.clone(obj.relationMgn);
        return newObj;
    }

    public void clear() {
        dictMgn = null;
        if (relationMgn != null) {
            relationMgn.clear();
            relationMgn = null;
        }
    }

    /**
     * Constructor with argument
     *
     * @param analyzer
     *            An {@link Analyzer} instance which is used to tokenize input text
     */
    public Chikkar(Analyzer analyzer) {
        this.dictMgn = DictionaryManager.getInstance();
        this.relationMgn = new RelationManager();
        this.analyzer = analyzer;
    }

    /**
     * Load a dictionary indicated by path. If this dictionary doesn't exist in
     * dictionary repository, it will be added into the repository first.
     *
     * @param path
     *            a dictionary to be loaded.
     * @throws IOException
     *             Throws {@link IOException} if error occur when reading dictionary
     */
    public void loadDictionary(Path path) throws IOException {
        if (Files.exists(path)) {
            dictMgn.addDictionary(relationMgn, path, analyzer, ++dictId);
        }
    }

    /**
     * Find synonymous result using active dictionaries specified by this Chikkar
     * instance.
     *
     * @param query
     *            the head word.
     * @return {@code List<String>} List of phrases that are synonymous of the given
     *         one.
     */
    public List<String> get(String query) {
        return dictMgn.findRelation(relationMgn, query);
    }

    /**
     * Given a String input, a start position(inclusive), and an end
     * position(exclusive), find the longest sub-string within the indicated
     * sub-string, that has a synonymous defined by this dictionary.
     *
     * @param input
     *            A whole string for query
     * @param start
     *            A position indicate the starting point in the given query string
     * @param end
     *            A position indicate the ending point in the given query string
     * @return {@code List<String>} List of phrases that are synonymous of the
     *         longest sub-string of the indicated sub-string.
     */
    public List<String> find(String input, int start, int end) {
        String longest = dictMgn.findLongestWordWithRelation(input, start, end);
        // return the related Entry with the longest word if found
        List<String> rtn = new ArrayList<>();
        if (longest.length() > 0) {
            rtn.addAll(get(longest));
        }
        return rtn;
    }

    public List<String> find(String input) {
        return find(input, 0, input.length());
    }

    /**
     * Given a {@code List<Morpheme>} input returned by Sudachi, a start
     * position(inclusive), and a end position(exclusive), find the longest
     * sub-string within the indicated sub-string, that has a synonymous defined by
     * this dictionary.
     *
     * @param input
     *            A {@code List<Morpheme>} returned by Sudachi
     * @param start
     *            A position indicate the starting point in the given list
     * @param end
     *            A position indicate the ending point in the given list
     * @return {@code List<String>} List of phrases that are synonymous of the
     *         longest sub-list of the indicated sub-list.
     */
    public List<String> find(List<Morpheme> input, int start, int end) {
        List<String> rtn = new ArrayList<>();
        Optional<String> longest = input.subList(start, end).stream().map(Morpheme::surface)
                .max(Comparator.comparing(String::length));
        if (longest.isPresent()) {
            rtn.addAll(get(longest.get()));
        }
        return rtn;
    }

    /**
     * Get all words which have the specified id.
     *
     * @param id
     *            An integer which stands for the word id.
     * @return List of words which have the specified id.
     */
    public List<String> getWordsFromId(int id) {
        return DictionaryManager.getInstance().getWordsFromId(id);
    }

    /**
     * Get all words stored in dictionary in dictionary order
     *
     * @return List of words
     */
    public List<String> getSortedKeys() {
        return DictionaryManager.getInstance().getSortedKeys();
    }

    /**
     * Get all synonym id of given query word. A {@code List<Integer>} is returned.
     *
     * @param query
     *            the head word.
     * @return A {@code List<Integer>}.
     */
    public List<Integer> getSynonymId(String query) {
        return dictMgn.findSynonymId(relationMgn, query);
    }
}
