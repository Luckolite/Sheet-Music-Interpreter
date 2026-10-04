// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original synthetic lifecycle regressions, using test-classpath fake public ORT API.
package io.github.luckolite.interpreter;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class LifecycleFaultControls {
    interface Job {
        void run() throws Exception;
    }

    static final List<Map<String, Object>> rows = new ArrayList<>();
    static Map<String, Object> observation;
    static boolean repaired;
    static final Set<String> BASELINE_FAILURES =
            Set.of(
                    "recognizer-Error",
                    "recognizer-Exception-detector-close-Exception",
                    "recognizer-Error-detector-close-Error",
                    "recognizer-Exception-options-and-detector-close-failures",
                    "options-close-Exception-after-both-sessions",
                    "options-close-Error-after-both-sessions",
                    "options-close-primary-both-cleanups-fail",
                    "dictionary-Error",
                    "dictionary-Exception-detector-close-Exception",
                    "dictionary-Exception-both-cleanups-fail",
                    "dictionary-Error-both-cleanups-Error",
                    "close-detector-Exception-recognizer-Error",
                    "close-detector-Error-recognizer-Exception");

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    static void need(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }

    static String desc(Throwable t) {
        return t == null ? null : t.getClass().getName() + ":" + t.getMessage();
    }

    static List<String> suppressed(Throwable t) {
        List<String> r = new ArrayList<>();
        if (t != null) for (Throwable v : t.getSuppressed()) r.add(desc(v));
        return r;
    }

    static void run(String name, Job job) throws Exception {
        FakeOrt.reset();
        observation = new LinkedHashMap<>();
        Throwable failure = null;
        try {
            job.run();
            capturedSettings();
        } catch (Throwable t) {
            failure = t;
        }
        boolean passed = failure == null,
                expectedPass = repaired || !BASELINE_FAILURES.contains(name);
        List<Map<String, Object>> sessionRows = new ArrayList<>();
        for (OrtSession s : FakeOrt.sessions)
            sessionRows.add(m("role", s.path, "closeCalls", s.closeCalls, "fakeClosed", s.closed));
        rows.add(
                m(
                        "name",
                        name,
                        "criteria",
                        passed ? "PASS" : "FAIL",
                        "expectedCriteria",
                        expectedPass ? "PASS" : "FAIL",
                        "expectedResult",
                        passed == expectedPass,
                        "assertion",
                        desc(failure),
                        "observed",
                        observation,
                        "sessionSettings",
                        new ArrayList<>(FakeOrt.sessionSettings),
                        "events",
                        new ArrayList<>(FakeOrt.events),
                        "sessions",
                        sessionRows,
                        "optionsCreated",
                        FakeOrt.optionsCreated,
                        "optionsCloseCalls",
                        FakeOrt.optionsCloseCalls));
        System.out.println(
                (passed ? "PASS " : "FAIL ")
                        + name
                        + (passed == expectedPass ? " [expected]" : " [UNEXPECTED]"));
        if (passed != expectedPass)
            throw new AssertionError("unexpected criterion status for " + name, failure);
    }

    static void capturedSettings() {
        for (var settings : FakeOrt.sessionSettings) {
            String role = (String) settings.get("role");
            need(
                    settings.get("intra").equals(role.equals("detector") ? 2 : 4),
                    "model-specific intra-op snapshot: " + settings);
            need(settings.get("inter").equals(1), "inter-op changed: " + settings);
            need(
                    settings.get("config").equals(Map.of("session.force_spinning_stop", "1")),
                    "spin-stop changed: " + settings);
        }
    }

    static void closeCounts(int detector, int recognizer, int options) {
        int actualDetector = 0, actualRecognizer = 0;
        for (OrtSession s : FakeOrt.sessions) {
            if (s.path.equals("detector")) actualDetector += s.closeCalls;
            else actualRecognizer += s.closeCalls;
        }
        need(
                actualDetector == detector,
                "detector close attempts=" + actualDetector + " expected=" + detector);
        need(
                actualRecognizer == recognizer,
                "recognizer close attempts=" + actualRecognizer + " expected=" + recognizer);
        need(
                FakeOrt.optionsCloseCalls == options,
                "options close attempts=" + FakeOrt.optionsCloseCalls + " expected=" + options);
    }

    static void primary(Throwable actual, Throwable expected, Throwable... cleanup) {
        observation.put("actualPrimary", desc(actual));
        observation.put("actualSuppressed", suppressed(actual));
        observation.put("expectedPrimary", desc(expected));
        need(actual == expected, "original primary identity lost: " + desc(actual));
        Throwable[] suppressed = actual.getSuppressed();
        need(
                suppressed.length == cleanup.length,
                "suppressed count=" + suppressed.length + " expected=" + cleanup.length);
        for (int i = 0; i < cleanup.length; i++)
            need(suppressed[i] == cleanup[i], "suppressed identity/order at " + i);
    }

    static void constructorFailure(
            Throwable expected, int detector, int recognizer, int options, Throwable... cleanup)
            throws Exception {
        Throwable actual = null;
        try {
            new OnnxOcrInference("detector", "recognizer");
        } catch (Throwable t) {
            actual = t;
        }
        primary(actual, expected, cleanup);
        closeCounts(detector, recognizer, options);
    }

    static void closeFailure(Throwable expected, Throwable... cleanup) throws Exception {
        OnnxOcrInference instance = new OnnxOcrInference("detector", "recognizer");
        Throwable actual = null;
        try {
            instance.close();
        } catch (Throwable t) {
            actual = t;
        }
        primary(actual, expected, cleanup);
        closeCounts(1, 1, 1);
    }

    static void success() throws Exception {
        OnnxOcrInference instance = new OnnxOcrInference("detector", "recognizer");
        List<String> expected =
                List.of(
                        "verify:detector",
                        "verify:recognizer",
                        "verify:recognizer.dictionary",
                        "options.new",
                        "intra:2",
                        "inter:1",
                        "config:session.force_spinning_stop=1",
                        "create:detector",
                        "intra:4",
                        "create:recognizer",
                        "options.close",
                        "dictionary:recognizer.dictionary:UTF-8");
        need(FakeOrt.events.equals(expected), "success constructor order/config changed");
        need(
                FakeOrt.sessionSettings.size() == 2,
                "Both model-session settings must be captured at creation");
        need(
                FakeOrt.sessionSettings.get(0).get("role").equals("detector")
                        && FakeOrt.sessionSettings.get(1).get("role").equals("recognizer"),
                "Model settings order changed");
        capturedSettings();
        need(instance.dictionary().equals(List.of("", "a", "b")), "dictionary contents");
        try {
            instance.dictionary().add("no");
            throw new AssertionError("dictionary mutable");
        } catch (UnsupportedOperationException ok) {
        }
        float[] input = {Float.intBitsToFloat(0x80000000), Float.intBitsToFloat(0x7fc12345), 1};
        int[] bits = {0x80000000, 0x7fc12345, 0x3f800000};
        need(
                instance.detect(input, 1, 1) == FakeOrt.detectorOutput,
                "detector output identity/math changed");
        need(
                Arrays.equals(FakeOrt.lastInputBits, bits)
                        && Arrays.equals(FakeOrt.lastInputShape, new long[] {1, 3, 1, 1}),
                "detector input changed");
        need(
                instance.recognize(input, 1, 1) == FakeOrt.recognizerOutput,
                "recognizer output identity/math changed");
        need(
                Arrays.equals(FakeOrt.lastInputBits, bits)
                        && Arrays.equals(FakeOrt.lastInputShape, new long[] {1, 3, 1, 1}),
                "recognizer input changed");
        for (int i = 0; i < input.length; i++)
            need(Float.floatToRawIntBits(input[i]) == bits[i], "caller raw bits changed");
        List<String> inferEvents = FakeOrt.events.subList(expected.size(), FakeOrt.events.size());
        need(
                inferEvents.equals(
                        List.of(
                                "tensor.new",
                                "run:detector",
                                "result.close:detector",
                                "tensor.close",
                                "tensor.new",
                                "run:recognizer",
                                "result.close:recognizer",
                                "tensor.close")),
                "inference resource order changed");
        need(
                FakeOrt.tensorsCreated == 2
                        && FakeOrt.tensorsClosed == 2
                        && FakeOrt.resultsCreated == 2
                        && FakeOrt.resultsClosed == 2,
                "infer resources");
        instance.close();
        closeCounts(1, 1, 1);
    }

    static String json(Object x) {
        if (x == null) return "null";
        if (x instanceof String s)
            return "\""
                    + s.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                    + "\"";
        if (x instanceof Boolean || x instanceof Number) return x.toString();
        if (x instanceof Map<?, ?> map) {
            List<String> a = new ArrayList<>();
            for (var e : map.entrySet())
                a.add(json(e.getKey().toString()) + ":" + json(e.getValue()));
            return "{" + String.join(",", a) + "}";
        }
        if (x instanceof Iterable<?> items) {
            List<String> a = new ArrayList<>();
            for (Object item : items) a.add(json(item));
            return "[" + String.join(",", a) + "]";
        }
        return json(x.toString());
    }

    public static void main(String[] args) throws Exception {
        repaired = args[0].equals("repaired");
        run("success-construct-infer-close", LifecycleFaultControls::success);
        run(
                "checksum-Exception-before-resources",
                () -> {
                    var e = new IOException("verify-primary");
                    FakeOrt.verifyFailure = e;
                    constructorFailure(e, 0, 0, 0);
                });
        run(
                "checksum-Error-before-resources",
                () -> {
                    var e = new AssertionError("verify-primary");
                    FakeOrt.verifyFailure = e;
                    constructorFailure(e, 0, 0, 0);
                });
        run(
                "options-constructor-Error",
                () -> {
                    var e = new AssertionError("options-new-primary");
                    FakeOrt.optionsNewFailure = e;
                    constructorFailure(e, 0, 0, 0);
                });
        run(
                "options-config-Exception",
                () -> {
                    var e = new OrtException("config-primary");
                    FakeOrt.configFailure = e;
                    constructorFailure(e, 0, 0, 1);
                });
        run(
                "recognizer-thread-config-Exception",
                () -> {
                    var e = new OrtException("recognizer-thread-primary");
                    FakeOrt.recognizerConfigFailure = e;
                    constructorFailure(e, 1, 0, 1);
                });
        run(
                "recognizer-thread-config-Error",
                () -> {
                    var e = new AssertionError("recognizer-thread-primary");
                    FakeOrt.recognizerConfigFailure = e;
                    constructorFailure(e, 1, 0, 1);
                });
        run(
                "detector-constructor-Exception",
                () -> {
                    var e = new OrtException("detector-primary");
                    FakeOrt.detectorCreateFailure = e;
                    constructorFailure(e, 0, 0, 1);
                });
        run(
                "detector-constructor-Error",
                () -> {
                    var e = new AssertionError("detector-primary");
                    FakeOrt.detectorCreateFailure = e;
                    constructorFailure(e, 0, 0, 1);
                });
        run(
                "recognizer-Exception",
                () -> {
                    var e = new OrtException("recognizer-primary");
                    FakeOrt.recognizerCreateFailure = e;
                    constructorFailure(e, 1, 0, 1);
                });
        run(
                "recognizer-Error",
                () -> {
                    var e = new AssertionError("recognizer-primary");
                    FakeOrt.recognizerCreateFailure = e;
                    constructorFailure(e, 1, 0, 1);
                });
        run(
                "recognizer-Exception-detector-close-Exception",
                () -> {
                    var e = new OrtException("recognizer-primary");
                    var c = new OrtException("detector-cleanup");
                    FakeOrt.recognizerCreateFailure = e;
                    FakeOrt.detectorCloseFailure = c;
                    constructorFailure(e, 1, 0, 1, c);
                });
        run(
                "recognizer-Error-detector-close-Error",
                () -> {
                    var e = new AssertionError("recognizer-primary");
                    var c = new AssertionError("detector-cleanup");
                    FakeOrt.recognizerCreateFailure = e;
                    FakeOrt.detectorCloseFailure = c;
                    constructorFailure(e, 1, 0, 1, c);
                });
        run(
                "recognizer-Exception-options-and-detector-close-failures",
                () -> {
                    var e = new OrtException("recognizer-primary");
                    var o = new IllegalStateException("options-cleanup");
                    var c = new OrtException("detector-cleanup");
                    FakeOrt.recognizerCreateFailure = e;
                    FakeOrt.optionsCloseFailure = o;
                    FakeOrt.detectorCloseFailure = c;
                    constructorFailure(e, 1, 0, 1, o, c);
                });
        run(
                "options-close-Exception-after-both-sessions",
                () -> {
                    var e = new IllegalStateException("options-primary");
                    FakeOrt.optionsCloseFailure = e;
                    constructorFailure(e, 1, 1, 1);
                });
        run(
                "options-close-Error-after-both-sessions",
                () -> {
                    var e = new AssertionError("options-primary");
                    FakeOrt.optionsCloseFailure = e;
                    constructorFailure(e, 1, 1, 1);
                });
        run(
                "options-close-primary-both-cleanups-fail",
                () -> {
                    var e = new IllegalStateException("options-primary");
                    var d = new OrtException("detector-cleanup");
                    var r = new AssertionError("recognizer-cleanup");
                    FakeOrt.optionsCloseFailure = e;
                    FakeOrt.detectorCloseFailure = d;
                    FakeOrt.recognizerCloseFailure = r;
                    constructorFailure(e, 1, 1, 1, d, r);
                });
        run(
                "dictionary-Exception",
                () -> {
                    var e = new IOException("dictionary-primary");
                    FakeOrt.dictionaryFailure = e;
                    constructorFailure(e, 1, 1, 1);
                });
        run(
                "dictionary-Error",
                () -> {
                    var e = new AssertionError("dictionary-primary");
                    FakeOrt.dictionaryFailure = e;
                    constructorFailure(e, 1, 1, 1);
                });
        run(
                "dictionary-Exception-detector-close-Exception",
                () -> {
                    var e = new IOException("dictionary-primary");
                    var c = new OrtException("detector-cleanup");
                    FakeOrt.dictionaryFailure = e;
                    FakeOrt.detectorCloseFailure = c;
                    constructorFailure(e, 1, 1, 1, c);
                });
        run(
                "dictionary-Exception-both-cleanups-fail",
                () -> {
                    var e = new IOException("dictionary-primary");
                    var d = new OrtException("detector-cleanup");
                    var r = new OrtException("recognizer-cleanup");
                    FakeOrt.dictionaryFailure = e;
                    FakeOrt.detectorCloseFailure = d;
                    FakeOrt.recognizerCloseFailure = r;
                    constructorFailure(e, 1, 1, 1, d, r);
                });
        run(
                "dictionary-Error-both-cleanups-Error",
                () -> {
                    var e = new AssertionError("dictionary-primary");
                    var d = new AssertionError("detector-cleanup");
                    var r = new AssertionError("recognizer-cleanup");
                    FakeOrt.dictionaryFailure = e;
                    FakeOrt.detectorCloseFailure = d;
                    FakeOrt.recognizerCloseFailure = r;
                    constructorFailure(e, 1, 1, 1, d, r);
                });
        run(
                "dictionary-null-List-copy",
                () -> {
                    FakeOrt.dictionaryWithNull = true;
                    Throwable actual = null;
                    try {
                        new OnnxOcrInference("detector", "recognizer");
                    } catch (Throwable t) {
                        actual = t;
                    }
                    need(
                            actual instanceof NullPointerException,
                            "List.copyOf null semantics changed");
                    closeCounts(1, 1, 1);
                });
        run(
                "constructor-self-suppression-guard",
                () -> {
                    var e = new OrtException("recognizer-primary");
                    FakeOrt.recognizerCreateFailure = e;
                    FakeOrt.detectorCloseFailure = e;
                    constructorFailure(e, 1, 0, 1);
                });
        run(
                "close-detector-Exception-recognizer-Error",
                () -> {
                    var d = new OrtException("detector-primary");
                    var r = new AssertionError("recognizer-cleanup");
                    FakeOrt.detectorCloseFailure = d;
                    FakeOrt.recognizerCloseFailure = r;
                    closeFailure(d, r);
                });
        run(
                "close-detector-Error-recognizer-Exception",
                () -> {
                    var d = new AssertionError("detector-primary");
                    var r = new OrtException("recognizer-cleanup");
                    FakeOrt.detectorCloseFailure = d;
                    FakeOrt.recognizerCloseFailure = r;
                    closeFailure(d, r);
                });
        run(
                "close-detector-Exception-recognizer-success",
                () -> {
                    var d = new OrtException("detector-primary");
                    FakeOrt.detectorCloseFailure = d;
                    closeFailure(d);
                });
        run(
                "close-detector-success-recognizer-Exception",
                () -> {
                    var r = new OrtException("recognizer-primary");
                    FakeOrt.recognizerCloseFailure = r;
                    closeFailure(r);
                });
        run(
                "close-self-suppression-guard",
                () -> {
                    var e = new AssertionError("same-primary-cleanup");
                    FakeOrt.detectorCloseFailure = e;
                    FakeOrt.recognizerCloseFailure = e;
                    closeFailure(e);
                });
        int failures = (int) rows.stream().filter(r -> r.get("criteria").equals("FAIL")).count();
        Files.writeString(
                Path.of(args[1]),
                json(
                                m(
                                        "status",
                                        "PASS",
                                        "mode",
                                        args[0],
                                        "criteria",
                                        rows.size(),
                                        "criterionPasses",
                                        rows.size() - failures,
                                        "criterionFailures",
                                        failures,
                                        "allExpected",
                                        true,
                                        "pid",
                                        ProcessHandle.current().pid(),
                                        "rows",
                                        rows,
                                        "scope",
                                        "Original fake API fault controls, no native sessions or models"))
                        + "\n");
        System.out.println(
                "TERMINAL PASS "
                        + args[0]
                        + " criteria="
                        + rows.size()
                        + " known failures="
                        + failures);
    }
}
