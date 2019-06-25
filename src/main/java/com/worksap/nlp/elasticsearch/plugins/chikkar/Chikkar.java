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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

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
    private final boolean restrictMode;

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
        this.restrictMode = false;
    }

    /**
     * Constructor with argument
     *
     * @param analyzer
     *            An {@link Analyzer} instance which is used to tokenize input text
     * @param restrictMode
     *            if true, then ignore all directed synonyms when parsing dictionary
     *            line
     */
    public Chikkar(Analyzer analyzer, boolean restrictMode) {
        this.dictMgn = DictionaryManager.getInstance();
        this.relationMgn = new RelationManager();
        this.analyzer = analyzer;
        this.restrictMode = restrictMode;
    }

    /**
     * Constructor with argument of all dictionary paths.
     *
     * @param analyzer
     *            An {@link Analyzer} instance which is used to tokenize input text
     * @param configPath
     *            path to the elastic-search config folder. As chikkar is run in
     *            elastic-search plugin, only the files under this config folder can
     *            be accessed.
     * @param dictList
     *            list of relative dict path under config folder
     * @param restrictMode
     *            if true, then ignore all directed synonyms when parsing dictionary
     *            line
     */
    public Chikkar(Analyzer analyzer, Path configPath, List<String> dictList, boolean restrictMode) {
        this.dictMgn = DictionaryManager.getInstance();
        this.relationMgn = new RelationManager();
        this.analyzer = analyzer;
        this.restrictMode = restrictMode;

        for (String dictPath : dictList) {
            dictMgn.addDictionary(relationMgn, configPath.resolve(dictPath).toString(), analyzer, restrictMode);
        }
    }

    /**
     * Constructor with argument of binary dictionary streams.
     *
     * @param analyzer
     *            An {@link Analyzer} instance which is used to tokenize input text
     * @param in
     *            A {@link FSTObjectInput} instance which contain binary dictionary
     *            streams
     * @throws IOException
     *             Throws {@link IOException} if binary dictionary streams have
     *             error
     * @throws ClassNotFoundException
     *             Throws {@link ClassNotFoundException} if read object can't be
     *             cast to specific class
     */
    public Chikkar(Analyzer analyzer, FSTObjectInput in) throws IOException, ClassNotFoundException {
        this.analyzer = analyzer;
        this.dictMgn = (DictionaryManager) in.readObject();
        this.relationMgn = (RelationManager) in.readObject();
        this.restrictMode = in.readBoolean();
    }

    /**
     * Save dictionary to streams
     *
     * @param out
     *            A {@link FSTObjectOutput} instance which stands for output streams
     *
     * @throws IOException
     *             Throws {@link IOException} if writing dictionary to streams have
     *             error
     */
    public void dumpToStream(FSTObjectOutput out) throws IOException {
        out.writeObject(dictMgn);
        out.writeObject(relationMgn);
        out.writeBoolean(restrictMode);
    }

    /**
     * Load a dictionary indicated by path. If this dictionary doesn't exist in
     * dictionary repository, it will be added into the repository first.
     *
     * @param path
     *            a dictionary to be loaded.
     */
    public void loadDictionary(String path) {
        dictMgn.addDictionary(relationMgn, path, analyzer, restrictMode);
    }

    /**
     * Find synonymous result using active dictionaries specified by this Chikkar
     * instance.
     *
     * @param query
     *            the head word.
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @param semanticTag
     *            semantic tag, value can be null.
     * @return {@code List<String>} List of phrases that are synonymous of the given
     *         one.
     */
    public List<String> get(String query, String pos, String pronun, String semanticTag) {
        return dictMgn.findRelation(relationMgn, query, pos, pronun, semanticTag);
    }

    /**
     * Find synonymous result using active dictionaries specified by this Chikkar
     * instance.
     *
     * @param query
     *            the head word.
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @return {@code List<String>} List of phrases that are synonymous of the given
     *         one.
     */
    public List<String> get(String query, String pos, String pronun) {
        return dictMgn.findRelation(relationMgn, query, pos, pronun, dictMgn.getDefaultSemanticTag(query, pos, pronun));
    }

    /**
     * Find synonymous result using active dictionaries specified by this Chikkar
     * instance.
     *
     * @param query
     *            The word used as query.
     * @return {@code List<String>} List of phrases that are synonymous of the given
     *         one.
     */
    public List<String> get(String query) {
        return get(query, null, null);
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
            rtn.addAll(get(longest, null, null));
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
            rtn.addAll(get(longest.get(), null, null));
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
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @param semanticTag
     *            semantic tag, value can be null.
     * @return A {@code List<Integer>}.
     */
    public List<Integer> getSynonymId(String query, String pos, String pronun, String semanticTag) {
        return dictMgn.findSynonymId(relationMgn, query, pos, pronun, semanticTag);
    }

    /**
     * Get all synonym id of given query word. A {@code List<Integer>} is returned.
     *
     * @param query
     *            the head word.
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @return A {@code List<Integer>}
     */
    public List<Integer> getSynonymId(String query, String pos, String pronun) {
        return dictMgn.findSynonymId(relationMgn, query, pos, pronun,
                dictMgn.getDefaultSemanticTag(query, pos, pronun));
    }

    /**
     * Get all synonym id of given query word. A {@code List<Integer>} is returned.
     *
     * @param query
     *            the head word.
     * @return A {@code List<Integer>}
     */
    public List<Integer> getSynonymId(String query) {
        return getSynonymId(query, null, null);
    }

}
