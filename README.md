# TeaVM rule support has moved

Implementation, tests and usage instructions live in
[Instanto TeaVM](https://github.com/instanto-io/instanto-teavm), in
`instanto-teavm-extensions`.

Replace `io.instanto:teavm-rule-support` with
`io.instanto:instanto-teavm-extensions:0.1.0-SNAPSHOT`. Declare it before
`org.teavm:teavm-junit`; do not include both. The shared version targets
TeaVM 0.16.0 and includes the lifecycle backport. ThreadLocal diagnostics
remain off unless explicitly enabled.

This repository now builds a relocation POM only. Publish the shared artifact
before merging and publishing this relocation. Git history retains the former
implementation.
