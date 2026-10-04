# Android native inference libraries

Android integrations need native libraries that work with 16 KB pages. A 16 KB PT_LOAD alignment alone does not verify the protected GNU_RELRO boundary.

Run `python scripts/android_native_layout.py app.aab --report native-layout.json` to check all ARM64 libraries. The helper also accepts APKs, AARs and individual ELF64 libraries. It checks program-header bounds, load alignment and congruence, and the GNU_RELRO end. It uses only the Python standard library. Seven generated ELF tests cover the old layout failure and valid layouts; no device logs or score scans are included.

The Music Sheets Android integration relinks four libraries from the same upstream SDK versions:

- ONNX Runtime 1.25.1, source commit `8a77e459420f58fb946fd9067285cfa719f10bdd`. Build Android ARM64/API 26 with NDK r28c, Release, full operators, Java JNI and NNAPI enabled. Set CMake shared and module linker flags to `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
- LiteRT 2.1.6, source commit `1461b6b2def31713f5c71446eab844aae05d02e9`. Build `//litert/kotlin:litert_jni` and `//litert/kotlin:LiteRt` with Bazel 7.7.0, Android ARM64/API 26, NDK r28c, dynamic runtime and both linker flags. The included Apache-2.0 source patch preserves the upstream library SONAMEs. Apply with `git apply --unidiff-zero docs/litert-2.1.6-soname.patch` from the LiteRT source checkout.

The app-local Maven closure retains the upstream Java classes, other ABIs and GPU accelerator. Its manifest records every retained entry, source pin and relinked-library hash. LiteRT Apache-2.0 and ONNX MIT licenses and third-party notices are included in the Android app. These app-specific artifacts are not bundled in this standalone interpreter.

On the same Android 15 16 KB emulator with ARM64 translation, original and relinked libraries produced byte-identical outputs for three generated raw segmentation inputs, OCR text and boxes, and a generated two-staff score containing 24 notes in eight measures. The final app release was checked against this baseline. This is synthetic output parity, not physical ARM64 testing or a general recognition-accuracy claim.

Models, Java decoder rules, guide records and the Windows worker format are unchanged. Windows uses PE libraries, so this Android ELF layout fix has no matching Windows relink. The app excludes an inactive Google OCR native pipeline; its local OCR remains in use. No app services, recordings, user libraries, signing material or SDK binaries are copied here.
