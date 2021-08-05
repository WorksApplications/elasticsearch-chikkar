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

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChikkarSynonymTokenFilterTest {

    private Analyzer analyzer;
    private TokenFilterFactory tokenFilterFactory;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        tempFolder.create();
        Path configPath = Paths.get(tempFolder.getRoot().getAbsolutePath());
        List<String> dictList = Arrays.asList("synonymMergeA.txt");
        analyzer = Utils.createWhitespaceAnalyzer();

        tokenFilterFactory = Utils.createChikkarTokenFilterFactory(configPath, dictList, analyzer);
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
        List<Utils.TokenAttribute> expected = Arrays.asList(new Utils.TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new Utils.TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("あいまい", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<Utils.TokenAttribute> result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new Utils.TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new Utils.TokenAttribute("不明確", "word", 0, 0, 3, 1),
                new Utils.TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new Utils.TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new Utils.TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1),
                new Utils.TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new Utils.TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new Utils.TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new Utils.TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new Utils.TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("荒筋", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 6
        query = "概要";
        expected = Arrays.asList(new Utils.TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("概要", "word", 0, 0, 2, 1),
                new Utils.TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 7
        query = "梗概";
        expected = Arrays.asList(new Utils.TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 8
        query = "荒筋";
        expected = Arrays.asList(new Utils.TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new Utils.TokenAttribute("荒筋", "word", 0, 0, 2, 1),
                new Utils.TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new Utils.TokenAttribute("粗筋", "SYNONYM", 0, 0, 4, 1),
                new Utils.TokenAttribute("荒筋", "SYNONYM", 0, 0, 4, 1),
                new Utils.TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new Utils.TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new Utils.TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = Utils.parseSynonyms(analyzer, tokenFilterFactory, query);
        Collections.sort(result);
        Utils.assertSynonymsEquals(expected, result);
    }
}
