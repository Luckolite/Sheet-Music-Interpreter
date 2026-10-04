// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package ai.onnxruntime;

import java.util.*;

public final class OrtSession implements AutoCloseable {
    public final String path;
    public int closeCalls;
    public boolean closed;

    OrtSession(String path) {
        this.path = path;
    }

    public Set<String> getInputNames() {
        return Set.of("x");
    }

    public Result run(Map<String, OnnxTensor> values) throws OrtException {
        if (closed || !values.keySet().equals(Set.of("x")))
            throw new AssertionError("invalid fake run");
        FakeOrt.events.add("run:" + path);
        return new Result(path);
    }

    public void close() throws OrtException {
        closeCalls++;
        FakeOrt.events.add("close:" + path);
        closed = true;
        FakeOrt.throwOrt(
                path.equals("detector")
                        ? FakeOrt.detectorCloseFailure
                        : FakeOrt.recognizerCloseFailure);
    }

    public static final class SessionOptions implements AutoCloseable {
        public int intraThreads, interThreads;
        public final Map<String, String> configEntries = new LinkedHashMap<>();

        public SessionOptions() {
            FakeOrt.events.add("options.new");
            FakeOrt.throwUnchecked(FakeOrt.optionsNewFailure);
            FakeOrt.optionsCreated++;
        }

        public void setIntraOpNumThreads(int n) throws OrtException {
            FakeOrt.events.add("intra:" + n);
            FakeOrt.throwOrt(FakeOrt.configFailure);
            FakeOrt.throwOrt(n == 4 ? FakeOrt.recognizerConfigFailure : null);
            intraThreads = n;
        }

        public void setInterOpNumThreads(int n) throws OrtException {
            FakeOrt.events.add("inter:" + n);
            interThreads = n;
        }

        public void addConfigEntry(String key, String value) throws OrtException {
            FakeOrt.events.add("config:" + key + "=" + value);
            configEntries.put(key, value);
        }

        public void close() {
            FakeOrt.optionsCloseCalls++;
            FakeOrt.events.add("options.close");
            FakeOrt.throwUnchecked(FakeOrt.optionsCloseFailure);
        }
    }

    public static final class Result implements AutoCloseable {
        private final String path;
        private boolean closed;

        Result(String path) {
            this.path = path;
            FakeOrt.resultsCreated++;
        }

        public OnnxTensor get(int index) {
            if (closed || index != 0) throw new AssertionError("result closed or index");
            return OnnxTensor.output(path);
        }

        public void close() {
            if (closed) throw new AssertionError("double result close");
            closed = true;
            FakeOrt.resultsClosed++;
            FakeOrt.events.add("result.close:" + path);
        }
    }
}
