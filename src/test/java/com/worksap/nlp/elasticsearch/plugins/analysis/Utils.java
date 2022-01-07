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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;

public class Utils {
    public static Analyzer createWhitespaceAnalyzer() {
        TokenizerFactory tokenizer = new WhitespaceTokenizerFactory();
        List<CharFilterFactory> charFilters = new ArrayList<>();
        List<TokenFilterFactory> tokenFilters = new ArrayList<>();

        return new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));
    }

    public static TokenFilterFactory createChikkarTokenFilterFactory(Path configPath, List<String> dictList,
            Analyzer analyzer) throws IOException {
        for (String dict : dictList) {
            Files.copy(Utils.class.getResourceAsStream("/" + dict), configPath.resolve(dict));
        }

        Chikkar chikkar = new Chikkar(analyzer);
        for (String dp : dictList) {
            chikkar.loadDictionary(configPath.resolve(dp));
        }
        ChikkarSynonymMap.Builder builder = new ChikkarSynonymMap.Builder(true);
        ChikkarSynonymMap synonyms = builder.build(chikkar);

        return new TokenFilterFactory() {
            @Override
            public String name() {
                return "chikkar_test";
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                return synonyms.fst == null ? tokenStream : new ChikkarSynonymTokenFilter(tokenStream, synonyms, false);
            }
        };
    }

    public static void assertSynonymsEquals(List<Utils.TokenAttribute> expected, List<Utils.TokenAttribute> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            Utils.TokenAttribute exp = expected.get(i);
            Utils.TokenAttribute act = actual.get(i);
            assertEquals(exp.term, act.term);
            assertEquals(exp.type, act.type);
            assertEquals(exp.position, act.position);
            assertEquals(exp.startOffset, act.startOffset);
            assertEquals(exp.endOffset, act.endOffset);
            assertEquals(exp.posLength, act.posLength);
        }
    }

    public static List<Utils.TokenAttribute> parseSynonyms(Analyzer analyzer, TokenFilterFactory tokenFilter,
            String text) throws IOException {
        if (analyzer == null) {
            analyzer = new CustomAnalyzer(new IdenticalTokenizerFactory(), new CharFilterFactory[0],
                    new TokenFilterFactory[0]);
        }

        TokenStream stream = tokenFilter.create(analyzer.tokenStream("", text));

        CharTermAttribute term = stream.getAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncr = stream.getAttribute(PositionIncrementAttribute.class);
        OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
        TypeAttribute type = stream.getAttribute(TypeAttribute.class);
        PositionLengthAttribute posLen = stream.getAttribute(PositionLengthAttribute.class);

        int lastPosition = -1;
        List<Utils.TokenAttribute> ta = new ArrayList<>();

        stream.reset();
        while (stream.incrementToken()) {
            lastPosition += posIncr.getPositionIncrement();
            ta.add(new Utils.TokenAttribute(term.toString(), type.type(), lastPosition, offset.startOffset(),
                    offset.endOffset(), posLen.getPositionLength()));
        }
        stream.end();
        stream.close();

        return ta;
    }

    public static class TokenAttribute implements Comparable<TokenAttribute> {
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

    public static class WhitespaceTokenizerFactory implements TokenizerFactory {
        @Override
        public String name() {
            return "WhitespaceTokenizer";
        }

        @Override
        public Tokenizer create() {
            return new WhitespaceTokenizer();
        }
    }

    static class IdenticalTokenizerFactory implements TokenizerFactory {
        static class IdenticalTokenizer extends CharTokenizer {
            @Override
            protected boolean isTokenChar(int c) {
                return true;
            }
        }

        @Override
        public String name() {
            return "IdenticalTokenizer";
        }

        @Override
        public Tokenizer create() {
            return new IdenticalTokenizer();
        }
    }
}
