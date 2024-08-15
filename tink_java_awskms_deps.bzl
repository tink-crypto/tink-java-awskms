"""Dependencies of Tink Java AWS KMS."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

TINK_JAVA_AWSKMS_MAVEN_TEST_ARTIFACTS = [
    "com.google.truth:truth:0.44",
    "junit:junit:4.13.2",
]

TINK_JAVA_AWSKMS_MAVEN_TOOLS_ARTIFACTS = [
    "org.ow2.asm:asm-commons:7.0",
    "org.ow2.asm:asm:7.0",
    "org.pantsbuild:jarjar:1.7.2",
]

TINK_JAVA_AWSKMS_MAVEN_ARTIFACTS = [
    "com.amazonaws:aws-java-sdk-core:1.12.560",
    "com.amazonaws:aws-java-sdk-kms:1.12.560",
    "com.google.auto.service:auto-service-annotations:1.1.1",
    "com.google.auto.service:auto-service:1.1.1",
    "com.google.auto:auto-common:1.2.2",
    "com.google.code.findbugs:jsr305:3.0.2",
    "com.google.errorprone:error_prone_annotations:2.22.0",
    "com.google.guava:guava:32.0.1-jre",
]

def tink_java_awskms_deps():
    """Bazel dependencies for tink-java-awskms."""
    if not native.existing_rule("tink_java"):
        # 2024-08-15
        http_archive(
            name = "tink_java",
            urls = ["https://github.com/tink-crypto/tink-java/releases/download/v1.14.1/tink-java-1.14.1.zip"],
            strip_prefix = "tink-java-1.14.1",
            sha256 = "1f8a2df8fcbfc9b19fba0d5f58cdcc12021cb33cdc307d22d840b437fd17347c",
        )
