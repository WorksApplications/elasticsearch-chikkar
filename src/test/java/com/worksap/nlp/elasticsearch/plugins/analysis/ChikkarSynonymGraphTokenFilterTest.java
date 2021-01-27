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

package com.worksap.nlp.elasticsearch.plugins.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;

public class ChikkarSynonymGraphTokenFilterTest {

    private Analyzer analyzer;
    private TokenFilterFactory tokenFilterFactory;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        TokenizerFactory tokenizer = new WhitespaceTokenizerFactory();
        List<CharFilterFactory> charFilters = new ArrayList<>();
        List<TokenFilterFactory> tokenFilters = new ArrayList<>();

        analyzer = new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));

        tempFolder.create();
        Path configPath = Paths.get(tempFolder.getRoot().getAbsolutePath());
        List<String> dictList = Arrays.asList("synonymMergeA.txt");

        for (String dict : dictList) {
            Files.copy(getClass().getResourceAsStream("/" + dict), configPath.resolve(dict));
        }

        Chikkar chikkar = new Chikkar(analyzer);
        for (String dp : dictList) {
            chikkar.loadDictionary(configPath.resolve(dp));
        }
        ChikkarSynonymMap.Builder builder = new ChikkarSynonymMap.Builder(true);
        ChikkarSynonymMap synonyms = builder.build(chikkar);

        tokenFilterFactory = new TokenFilterFactory() {
            @Override
            public String name() {
                return "chikkar_test";
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                return synonyms.fst == null ? tokenStream
                        : new ChikkarSynonymGraphTokenFilter(tokenStream, synonyms, false);
            }
        };
    }

    @Test
    public void testGetSynonyms() throws Exception {
        /*
         * 曖昧,不明確,あやふや,あいまい 粗筋,概略,大略,概要,大要,要約,要旨,梗概 粗筋,荒筋,あらすじ
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あいまい", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new TokenAttribute("不明確", "word", 0, 0, 3, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("荒筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "概要";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "word", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "梗概";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "荒筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("荒筋", "word", 0, 0, 2, 1), new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("荒筋", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(tokenFilterFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    void assertSynonymsEquals(List<TokenAttribute> expected, List<TokenAttribute> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            TokenAttribute exp = expected.get(i);
            TokenAttribute act = actual.get(i);
            assertEquals(exp.term, act.term);
            assertEquals(exp.type, act.type);
            assertEquals(exp.position, act.position);
            assertEquals(exp.startOffset, act.startOffset);
            assertEquals(exp.endOffset, act.endOffset);
            assertEquals(exp.posLength, act.posLength);
        }
    }

    List<TokenAttribute> parseSynonyms(TokenFilterFactory tokenFilter, String text) throws IOException {
        TokenStream stream = tokenFilter.create(analyzer.tokenStream("", text));

        CharTermAttribute term = stream.getAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncr = stream.getAttribute(PositionIncrementAttribute.class);
        OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
        TypeAttribute type = stream.getAttribute(TypeAttribute.class);
        PositionLengthAttribute posLen = stream.getAttribute(PositionLengthAttribute.class);

        int lastPosition = -1;
        List<TokenAttribute> ta = new ArrayList<>();

        stream.reset();
        while (stream.incrementToken()) {
            lastPosition += posIncr.getPositionIncrement();
            ta.add(new TokenAttribute(term.toString(), type.type(), lastPosition, offset.startOffset(),
                    offset.endOffset(), posLen.getPositionLength()));
        }
        stream.end();
        stream.close();

        return ta;
    }

    class WhitespaceTokenizerFactory implements TokenizerFactory {
        @Override
        public String name() {
            return "WhitespaceTokenizer";
        }

        @Override
        public Tokenizer create() {
            return new WhitespaceTokenizer();
        }
    }

    class TokenAttribute implements Comparable<TokenAttribute> {
        public String term;
        public String type;
        public int position;
        public int startOffset;
        public int endOffset;
        public int posLength;

        public TokenAttribute(String term, String type, int position, int startOffset, int endOffset, int posLength) {
            this.term = term;
            this.type = type;
            this.position = position;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.posLength = posLength;
        }

        public String toString() {
            return String.format(
                    "Term: %s, Type: %s, Position: %d, StartOffset: %d, EndOffset: %d, " + "PositionLength: %d", term,
                    type, position, startOffset, endOffset, posLength);
        }

        @Override
        public int compareTo(TokenAttribute obj) {
            return this.toString().compareTo(obj.toString());
        }
    }

}
