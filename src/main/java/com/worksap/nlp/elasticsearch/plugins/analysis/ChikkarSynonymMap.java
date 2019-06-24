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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.ByteSequenceOutputs;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.Util;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;

public class ChikkarSynonymMap {
    // for multiword support, you must separate words with this separator
    private static final int LONG_BYTES = 8;
    public static final char WORD_SEPARATOR = '\1';
    public final Chikkar chikkar;
    public final FST<BytesRef> fst;

    /**
     * Constructor with argument
     *
     * @param chikkar
     *            A {@link Chikkar} instance used for building FST
     * @param fst
     *            A {@link org.apache.lucene.util.fst.FST} instance for synonym
     *            matching
     */
    public ChikkarSynonymMap(Chikkar chikkar, FST<BytesRef> fst) {
        this.chikkar = chikkar;
        this.fst = fst;
    }

    /**
     * Save built chikkar instance to binary files
     *
     * @param chikkarPath
     *            local file path of the dumped chikkar instance
     * @throws IOException
     *             Throws {@link IOException} if error happens during saving files
     */
    public void dump(Path chikkarPath) throws IOException {
        try (CountingOutputStream countOut = new CountingOutputStream(
                new BufferedOutputStream(new FileOutputStream(chikkarPath.toFile())))) {

            final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

            try (FSTObjectOutput out = conf.getObjectOutput(countOut)) {
                chikkar.dumpToStream(out);
                out.flush();

                long byteCount = countOut.getCount();
                out.writeLong(byteCount);
                out.flush();

                int realBytes = (int) (countOut.getCount() - byteCount);
                if (realBytes < LONG_BYTES) {
                    out.write(new byte[LONG_BYTES - realBytes]);
                    out.flush();
                }

                fst.save(new OutputStreamDataOutput(countOut));
            }
        }
    }

    /**
     * Restore {@link ChikkarSynonymMap} instance from binary files
     *
     * @param chikkarPath
     *            local file path of the dumped chikkar instance
     * @param analyzer
     *            An {@link Analyzer} instance which is used to tokenize input text
     * @return A {@link ChikkarSynonymMap} instance restored from binary files
     *
     * @throws IOException
     *             Throws {@link IOException} if error happens during reading files
     * @throws ClassNotFoundException
     *             Throws {@link ClassNotFoundException} if read object can't be
     *             cast to specific class
     */
    public static ChikkarSynonymMap read(Path chikkarPath, Analyzer analyzer)
            throws IOException, ClassNotFoundException {

        byte[] bytes = ByteStreams.toByteArray(new FileInputStream(chikkarPath.toFile()));

        final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

        try (FSTObjectInput in = conf.getObjectInput(new ByteArrayInputStream(bytes))) {
            Chikkar chikkar = new Chikkar(analyzer, in);

            long readBytes = in.readLong();
            int offSet = (int) (readBytes + LONG_BYTES);

            DataInput dataIn = new InputStreamDataInput(new ByteArrayInputStream(bytes, offSet, bytes.length - offSet));
            FST<BytesRef> fst = new FST<>(dataIn, ByteSequenceOutputs.getSingleton());

            return new ChikkarSynonymMap(chikkar, fst);
        }
    }

    public static class Builder {
        private final boolean dedup;

        /**
         * Default constructor, passes {@code dedup=true}.
         */
        public Builder() {
            this(true);
        }

        /**
         * Constructor with argument
         *
         * @param dedup
         *            If dedup is true then identical rules (same input, same output)
         *            will be added only once.
         */
        public Builder(boolean dedup) {
            this.dedup = dedup;
        }

        /**
         * Builds a {@link ChikkarSynonymMap} instance and returns it.
         *
         * @param chikkar
         *            A {@link Chikkar} instance used for building FST
         * @return A {@link ChikkarSynonymMap} instance
         * @throws IOException
         *             throws IOException if fail to build the ChikkarSynonymMap
         */
        public ChikkarSynonymMap build(Chikkar chikkar) throws IOException {
            ByteSequenceOutputs outputs = ByteSequenceOutputs.getSingleton();
            // TODO: are we using the best sharing options?
            org.apache.lucene.util.fst.Builder<BytesRef> builder = new org.apache.lucene.util.fst.Builder<>(
                    FST.INPUT_TYPE.BYTE4, outputs);

            BytesRefBuilder scratch = new BytesRefBuilder();
            ByteArrayDataOutput scratchOutput = new ByteArrayDataOutput();

            final Set<Integer> dedupSet;

            if (dedup) {
                dedupSet = new HashSet<>();
            } else {
                dedupSet = null;
            }

            final byte[] spare = new byte[5];
            List<String> keys = chikkar.getSortedKeys();

            final IntsRefBuilder scratchIntsRef = new IntsRefBuilder();

            for (String input : keys) {
                List<Integer> ords = chikkar.getSynonymId(input);
                if (ords.isEmpty()) {
                    continue;
                }

                int numEntries = ords.size();
                // output size, assume the worst case
                int estimatedSize = 5 + numEntries * 5; // numEntries + one ord for each entry

                scratch.grow(estimatedSize);
                scratchOutput.reset(scratch.bytes());

                // now write our output data:
                int count = 0;
                for (int i = 0; i < numEntries; i++) {
                    if (dedupSet != null) {
                        // box once
                        final Integer ent = ords.get(i);
                        if (dedupSet.contains(ent)) {
                            continue;
                        }
                        dedupSet.add(ent);
                    }
                    scratchOutput.writeVInt(ords.get(i));
                    count++;
                }

                final int pos = scratchOutput.getPosition();
                scratchOutput.writeVInt(count << 1);
                final int pos2 = scratchOutput.getPosition();
                final int vIntLen = pos2 - pos;

                // Move the count + includeOrig to the front of the byte[]:
                System.arraycopy(scratch.bytes(), pos, spare, 0, vIntLen);
                System.arraycopy(scratch.bytes(), 0, scratch.bytes(), vIntLen, pos);
                System.arraycopy(spare, 0, scratch.bytes(), 0, vIntLen);

                if (dedupSet != null) {
                    dedupSet.clear();
                }

                scratch.setLength(scratchOutput.getPosition());
                builder.add(Util.toUTF32(input, scratchIntsRef), scratch.toBytesRef());
            }

            FST<BytesRef> fst = builder.finish();
            return new ChikkarSynonymMap(chikkar, fst);
        }
    }

}
