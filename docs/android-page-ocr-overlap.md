<!-- Copyright 2026 Luckolite
SPDX-License-Identifier: Apache-2.0 -->

# Android local page OCR overlap

This contribution documents an Android adapter change in
`OmrMeasureAnalyzer`, `PageTextRecognition` and `PortablePageOcr`. The
standalone CLI has no counterpart for these page orchestration classes. No
standalone scheduler, dependency, model or unused production wrapper is added.

The local path prepares read-only tab geometry once. On a proved non-tab page,
it may start full-page OCR before segmentation; on a tab page, it retains the
original tab crop/header request order and starts full-page OCR afterward,
before geometry decoding. Only the opted-in `LocalPrefetchReader` capability
uses its reader's original serial await contract. Existing managed prefetch
keeps its timed `Future.get`, wrapped `ExecutionException` causes and
completed-failure cancellation attempts. Segmentation retains failure
priority over a deferred ordinary tab indexing failure.

These statements describe D's orchestration delta. D does not change the
mapped `PortableOcr` tensor/CTC logic, optional `OnnxOcrInference`, native tile
owners, packing or stitching. The combined source separately includes the
reviewed gray normalization change and seventh-line tab bounds repair; those
changes have their own provenance. The combined compatibility namespace is
guide layout 282, note layout 23 and recognition revision 5. D changes none of
those record layouts or model bytes.

The reviewed composed source identities are:

| APP-relative source | SHA-256 |
| --- | --- |
| `app/src/main/java/com/musicsheets/app/pdf/OmrMeasureAnalyzer.java` | `ee334684981f952f7ecde6867a570fe3310dd9dd43b4f3c53c297cae1b27992e` |
| `app/src/main/java/com/musicsheets/app/pdf/PortablePageOcr.java` | `0906b58d68907e33d036d607a6eb3f190126ceeb7734900363d4810649fb5656` |
| `app/src/main/java/com/musicsheets/app/importer/PageTextRecognition.java` | `07b9eace96503b9dcda796dd5d4b51e9c8ccc622e53073a82aca4fe877848a94` |

## Original synthetic Android regressions

The following are exact new method excerpts from
`app/src/androidTest/java/com/musicsheets/app/importer/PageTextRecognitionInstrumentedTest.java`
(SHA-256 `1d43cab45edf83a06c3c4fa6da651cd4a399a2ef628ea2c064feff5855a54b1d`).
They belong to the existing Android instrumentation class and use its Android
`Bitmap`/`Color`, `PageTextRecognition`, JUnit `@Test` and assertion imports.
`GRAY_TEXT` is the existing synthetic `OcrText` fixture value, used only as the
uninvoked serial result of the unspecified reader in these methods.
These excerpts are Apache-2.0 validation examples, not new standalone Java
tests. They need Android APIs but no model inference or native owner creation.

The needed fixture helpers call the actual existing `PortablePageOcr.await`
method; they do not reproduce a scheduler or its wait implementation:

```java
    private static com.musicsheets.app.pdf.OcrText adapterAwait(
            java.util.concurrent.Future<com.musicsheets.app.pdf.OcrText> future,int seconds)throws Exception {
        var method=com.musicsheets.app.pdf.PortablePageOcr.class.getDeclaredMethod(
                "await",java.util.concurrent.Future.class,int.class);method.setAccessible(true);
        try{return (com.musicsheets.app.pdf.OcrText)method.invoke(null,future,seconds);}
        catch(java.lang.reflect.InvocationTargetException wrapped) {
            if(wrapped.getCause() instanceof Exception e)throw e;
            if(wrapped.getCause() instanceof Error e)throw e;throw new AssertionError(wrapped.getCause());
        }
    }


    private static final class CancelRecordingEvidenceFuture extends
            java.util.concurrent.CompletableFuture<com.musicsheets.app.pdf.OcrText> {
        int cancelCalls;
        boolean lastMayInterrupt;
        @Override public boolean cancel(boolean mayInterrupt){
            cancelCalls++;lastMayInterrupt=mayInterrupt;return super.cancel(mayInterrupt);
        }
    }
    private static final class LocalAwaitFixture implements PageTextRecognition.LocalPrefetchReader {
        final CancelRecordingEvidenceFuture future;
        LocalAwaitFixture(CancelRecordingEvidenceFuture future){this.future=future;}
        public com.musicsheets.app.pdf.OcrText read(Bitmap b,int seconds)throws Exception {
            return adapterAwait(future,seconds);
        }
        public java.util.concurrent.Future<com.musicsheets.app.pdf.OcrText> start(Bitmap b){return future;}
        public com.musicsheets.app.pdf.OcrText awaitStarted(
                java.util.concurrent.Future<com.musicsheets.app.pdf.OcrText> work,int seconds)throws Exception {
            return adapterAwait(work,seconds);
        }
    }
```

```java
    @Test public void managedPrefetchKeepsWrappedNativeFailuresAndCompletedFailureCancelAttempts()throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);
        try {
            for(Throwable cause:new Throwable[]{new java.io.IOException("native failure"),new AssertionError("native error")}) {
                var failed=new CancelRecordingEvidenceFuture();failed.completeExceptionally(cause);
                // Even a capable reader must keep the historical mode through the existing API.
                try(var backend=PageTextRecognition.withEvidenceReader(new LocalAwaitFixture(failed))) {
                    var session=PageTextRecognition.begin();
                    assertTrue(PageTextRecognition.prefetch(bitmap,null));
                    var wrapped=assertThrows(java.util.concurrent.ExecutionException.class,
                            ()->PageTextRecognition.readText(bitmap,null,1));
                    assertSame(cause,wrapped.getCause());assertEquals(1,failed.cancelCalls);
                    assertTrue(failed.lastMayInterrupt);assertFalse(failed.isCancelled());
                    assertSame(wrapped,assertThrows(IllegalStateException.class,session::close).getCause());
                }
                try(var clean=PageTextRecognition.begin()){assertEquals(0,clean.executions());}
            }
        } finally {bitmap.recycle();}
    }
```

```java
    @Test public void localPrefetchMatchesSerialNativeCausesAndRejectsUnspecifiedAwaitContracts()throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);int generation=bitmap.getGenerationId();
        try {
            int[] starts={0};
            PageTextRecognition.EvidenceReader unspecified=new PageTextRecognition.EvidenceReader() {
                public com.musicsheets.app.pdf.OcrText read(Bitmap b,int seconds){return GRAY_TEXT;}
                public java.util.concurrent.Future<com.musicsheets.app.pdf.OcrText> start(Bitmap b){starts[0]++;return null;}
            };
            try(var backend=PageTextRecognition.withEvidenceReader(unspecified);var session=PageTextRecognition.begin()) {
                assertFalse(PageTextRecognition.supportsLocalPrefetch());
                assertFalse(PageTextRecognition.prefetchLocal(bitmap));assertEquals(0,starts[0]);
            }
            for(boolean prefetch:new boolean[]{false,true})
                for(Throwable cause:new Throwable[]{new java.io.IOException("native failure"),new AssertionError("native error")}) {
                    var failed=new CancelRecordingEvidenceFuture();failed.completeExceptionally(cause);
                    try(var backend=PageTextRecognition.withEvidenceReader(new LocalAwaitFixture(failed))) {
                        var session=PageTextRecognition.begin();assertTrue(PageTextRecognition.supportsLocalPrefetch());
                        if(prefetch)assertTrue(PageTextRecognition.prefetchLocal(bitmap));
                        if(cause instanceof Error)assertSame(cause,assertThrows(AssertionError.class,
                                ()->PageTextRecognition.readText(bitmap,null,1)));
                        else assertSame(cause,assertThrows(java.io.IOException.class,
                                ()->PageTextRecognition.readText(bitmap,null,1)));
                        assertEquals(0,failed.cancelCalls);assertFalse(failed.isCancelled());
                        assertEquals(1,session.executions());
                        if(cause instanceof Error)session.close();
                        else assertSame(cause,assertThrows(IllegalStateException.class,session::close).getCause());
                    }
                    assertEquals(generation,bitmap.getGenerationId());
                    try(var clean=PageTextRecognition.begin()){assertEquals(0,clean.executions());}
                }
        } finally {bitmap.recycle();}
    }
```

```java
    @Test public void localAndManagedPrefetchRetainTimeoutAndInterruptCancellationBoundaries()throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);
        boolean incoming=Thread.interrupted();
        try {
            // Serial, historical prefetch and opt-in local prefetch have the same direct wait failures.
            for(int mode=0;mode<3;mode++)for(boolean interrupt:new boolean[]{false,true}) {
                var pending=new CancelRecordingEvidenceFuture();
                try(var backend=PageTextRecognition.withEvidenceReader(new LocalAwaitFixture(pending))) {
                    var session=PageTextRecognition.begin();
                    if(mode==1)assertTrue(PageTextRecognition.prefetch(bitmap,null));
                    if(mode==2)assertTrue(PageTextRecognition.prefetchLocal(bitmap));
                    Exception cause;
                    if(interrupt) {
                        Thread.currentThread().interrupt();
                        cause=assertThrows(InterruptedException.class,()->PageTextRecognition.readText(bitmap,null,1));
                        assertTrue(Thread.currentThread().isInterrupted());Thread.interrupted();
                    } else cause=assertThrows(java.util.concurrent.TimeoutException.class,
                            ()->PageTextRecognition.readText(bitmap,null,0));
                    assertEquals(1,pending.cancelCalls);assertTrue(pending.lastMayInterrupt);assertTrue(pending.isCancelled());
                    assertSame(cause,assertThrows(IllegalStateException.class,session::close).getCause());
                }
                try(var clean=PageTextRecognition.begin()){assertEquals(0,clean.executions());}
            }
        } finally {Thread.interrupted();if(incoming)Thread.currentThread().interrupt();bitmap.recycle();}
    }
```

## Evidence boundary

This document records source review and synthetic control source. APP main
compilation, 7,470 unit tests (7,468 passed, 2 skipped, 0 failed), and lint (0 errors)
completed successfully. AndroidTest compilation first stopped on two inherited
PdfDocument fixtures. After those fixture corrections, AndroidTest compilation and debug/test APK
packaging completed successfully. Execution of the three methods remains
pending. These are not executed standalone regressions.

The original composed-source manifest
`44241b737523fac80e799fbe9770335352c2146fb79d4428117f4856db52ff1d`
is historical. The Android test-fixture continuation source is bound by
manifest `48e1b9954f763e94732ec361039119a599ccfc59224201d600ec5c066d4decd1`.
That binding describes the Android continuation inputs. Later private C# smoke
changes can alter the effective whole-source manifest without changing these
Android inputs. The optional native-owner
control is excluded: its original seventh-row exception assertion does not
apply after the separately admitted Tab bounds repair.

Earlier allocations, speculative OCR work and physical timeout occurrence can
change. A queued-task cancellation is not proof that in-flight native work has
completed. Actual native OCR completion, successful segmentation/decoder
output, full-score/guide parity and phone elapsed improvement remain unproved.
No private score fixtures, device logs, model bytes or signing material are
included.
