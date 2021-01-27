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
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Character.isDigit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymMap;

/**
 * DictionaryManager functions as a dictionary repository, which stores and
 * returns dictionaries. The class is defined as a singleton instance so that
 * all Chikkar instances have access to the same repository.
 *
 * @author zhao_ya@worksap.co.jp &amp; xiao_w@worksap.co.jp &amp;
 *         liu_to@worksap.co.jp
 */

class DictionaryManager implements Serializable {
    EntryManager entryMgn;

    private static DictionaryManager dictMgn;

    enum LoadType {
        ADD, DIRECTED, SKIP
    }

    class LoadResult {
        LoadType loadType;

        public LoadResult(LoadType loadType) {
            this.loadType = loadType;
        }

        public LoadType getLoadType() {
            return this.loadType;
        }
    }

    private DictionaryManager() {
        entryMgn = new EntryManager();
    }

    /**
     * Get singleton instance of DictionaryManager.
     *
     * @return The singleton instance of DictionaryManager
     */
    public static DictionaryManager getInstance() {
        if (dictMgn == null) {
            dictMgn = new DictionaryManager();
        }
        return dictMgn;
    }

    /**
     * Returns a result for a query based on the relation defined in this
     * dictionary. Since a phrase may have more than one phrases that satisfy the
     * relation, a {@code List<String>} is returned.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param query
     *            the head word.
     * @return {@code List<String>} List of phrases that satisfies the relation.
     */
    public List<String> findRelation(RelationManager relationMgn, String query) {
        List<String> rtn = new ArrayList<>();
        if (!entryMgn.retrieveEntry(query).isEmpty()) {
            int u = entryMgn.retrieveEntry(query).get(0);
            rtn = entryMgn.getWordsFromId(relationMgn.getRelationMatrix().getRelations(u));
        }
        return rtn;
    }

    public String findLongestWordWithRelation(String input, int start, int end) {
        return entryMgn.findLongestWordWithRelation(input, start, end);
    }

    /**
     * Get all words stored in dictionary in dictionary order
     *
     * @return List of words
     */
    public List<String> getSortedKeys() {
        return entryMgn.getSortedKeys();
    }

    /**
     * Get all synonym id of given query word. A {@code List<Integer>} is returned.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param query
     *            the head word.
     * @return List of all synonym id
     */
    public List<Integer> findSynonymId(RelationManager relationMgn, String query) {
        List<Integer> entries = entryMgn.retrieveEntry(query);
        if (!entries.isEmpty()) {
            int u = entryMgn.retrieveEntry(query).get(0);
            return relationMgn.getRelationMatrix().getRelations(u).stream().filter(r -> r != u)
                    .collect(Collectors.toList());
        }
        return entries;
    }

    /**
     * Get all words which have the specified id.
     *
     * @param id
     *            An integer which stands for the word id.
     * @return List of words which have the specified id.
     */
    public List<String> getWordsFromId(int id) {
        return entryMgn.getWordsFromId(id);
    }

    /**
     * Parse one dictionary line
     *
     * @param analyzer
     *            An analyzer instance which is used to analyze the entries in
     *            dictionary.
     * @param line
     *            The line to parse.
     * @param baseWords
     *            A list contains base words of the line
     * @param relatives
     *            A list contains relative words of the line
     * @return LoadResult return load type and semantic tag.
     */
    LoadResult loadDictionaryLine(Analyzer analyzer, String line, ArrayList<Integer> baseWords,
            ArrayList<Integer> relatives) {
        // TODO parse dictionary file, may need modification if dictionary format change

        // SKIP
        if (line.length() < 1)
            return new LoadResult(LoadType.SKIP);
        if (line.charAt(0) == '!' && line.charAt(1) == '!')
            return new LoadResult(LoadType.SKIP);
        if (isDigit(line.charAt(0)))
            return new LoadResult(LoadType.SKIP);

        // DIRECTED
        if (line.split("=>").length > 1) {
            String[] directions = line.split("=>");

            for (int i = 0; i < directions.length; i++) {
                String[] temp = directions[i].split(",");
                for (int j = 0; j < temp.length; j++) {
                    String word = temp[j].trim();
                    if (!word.isEmpty()) {
                        List<String> resList = analyze(analyzer, word);
                        for (String res : resList) {
                            if (entryMgn.retrieveEntry(res).isEmpty()) {
                                entryMgn.insertEntry(res, new Entry());
                            }
                            if (i == 0) {
                                baseWords.add(entryMgn.retrieveEntry(res).get(0));
                            } else {
                                relatives.add(entryMgn.retrieveEntry(res).get(0));
                            }
                        }
                    }
                }
            }
            return new LoadResult(LoadType.DIRECTED);
        } else {
            String[] words = line.split(",");
            fillWordsToIds(analyzer, words, baseWords, relatives);
            return new LoadResult(LoadType.ADD);
        }
    }

    void fillWordsToIds(Analyzer analyzer, String[] words, ArrayList<Integer> baseWords, ArrayList<Integer> relatives) {
        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            if (!word.isEmpty()) {
                List<String> resList = analyze(analyzer, word);
                for (String res : resList) {
                    if (entryMgn.retrieveEntry(res).isEmpty()) {
                        entryMgn.insertEntry(res, new Entry());
                    }
                    if (i == 0) {
                        baseWords.add(entryMgn.retrieveEntry(res).get(0));
                    } else {
                        relatives.add(entryMgn.retrieveEntry(res).get(0));
                    }
                }
            }
        }
    }

    /**
     * Add new dictionary into DictionaryManager.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param dictPath
     *            A dictionary path.
     * @param analyzer
     *            An analyzer instance which is used to analyze the entries in
     *            dictionary. As DictionaryManager is singleton instance, and
     *            different users may use different analyzers(e.g. Japanese or
     *            English), so we need to pass the specific analyzer instance when
     *            addDictionary.
     * @throws IOException
     *             Throws {@link IOException} if error occur when reading dictionary
     */
    public synchronized void addDictionary(RelationManager relationMgn, Path dictPath, Analyzer analyzer, int dictId)
            throws IOException {
        // set the default relationSet as sparse matrix
        RelationManager.RelationMatrix relationMatrix = relationMgn.getRelationMatrix();

        try (Stream<String> input = Files.lines(dictPath, StandardCharsets.UTF_8)) {
            input.forEach(line -> {
                ArrayList<Integer> baseWords = new ArrayList<>();
                ArrayList<Integer> relatives = new ArrayList<>();
                LoadResult loadResult = loadDictionaryLine(analyzer, line, baseWords, relatives);

                switch (loadResult.getLoadType()) {
                case ADD:
                    addLine(baseWords, relatives, relationMatrix, dictId);
                    break;
                case DIRECTED:
                    addDirectedLine(baseWords, relatives, relationMatrix, dictId);
                    break;
                case SKIP:
                    break;
                }
            });
        }
    }

    /**
     * Analyzes the text with the analyzer and separates by
     * {@link ChikkarSynonymMap#WORD_SEPARATOR}.
     *
     * @param analyzer
     *            An analyzer instance which is used to analyze the input text
     * @param text
     *            input text
     * @return List of analyzed result text
     */
    List<String> analyze(Analyzer analyzer, String text) {
        CharsRefBuilder reuse = new CharsRefBuilder();
        reuse.clear();

        List<String> tlist = new ArrayList<>();

        try {
            TokenStream ts = analyzer.tokenStream("", text);
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            PositionLengthAttribute posLen = ts.addAttribute(PositionLengthAttribute.class);
            ts.reset();

            while (ts.incrementToken()) {
                int length = termAtt.length();
                if (length == 0) {
                    return tlist;
                }

                if (posLen.getPositionLength() > 1) {
                    // skip duplicate tokens with coarse granularity when sudachi / kuromoji is set
                    // to search mode
                    if (length == text.length()) {
                        tlist.add(text);
                    }
                    continue;
                }

                reuse.grow(reuse.length() + length + 1); /* current + word + separator */
                int end = reuse.length();
                if (reuse.length() > 0) {
                    reuse.setCharAt(end++, ChikkarSynonymMap.WORD_SEPARATOR);
                    reuse.setLength(reuse.length() + 1);
                }
                System.arraycopy(termAtt.buffer(), 0, reuse.chars(), end, length);
                reuse.setLength(reuse.length() + length);
            }

            ts.end();
            ts.close();
        } catch (IOException e) {
            return tlist;
        }

        if (reuse.length() == 0) {
            return tlist;
        }
        CharsRef ref = reuse.get();
        tlist.add(new String(ref.chars, ref.offset, ref.length));
        return tlist;
    }

    void addLine(ArrayList<Integer> baseWords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix, int dictId) {
        ArrayList<Integer> words = new ArrayList<>();
        words.addAll(baseWords);
        words.addAll(relatives);
        words.forEach(a -> words.forEach(b -> {
            relationMatrix.add(a, b, dictId);
        }));
    }

    void addDirectedLine(ArrayList<Integer> baseWords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix, int dictId) {
        baseWords.forEach(a -> relatives.forEach(b -> {
            relationMatrix.add(a, b, dictId);
        }));
    }
}
