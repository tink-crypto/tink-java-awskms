"""Dependencies of Tink Java AWS KMS."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

TINK_JAVA_AWSKMS_MAVEN_TEST_ARTIFACTS = [
    "com.google.truth:truth:1.4.4",
    "org.junit.jupiter:junit-jupiter-api:5.11.3",
]

TINK_JAVA_AWSKMS_MAVEN_TOOLS_ARTIFACTS = [
    "org.ow2.asm:asm-commons:9.7.1",
    "org.ow2.asm:asm:9.7.1",
    "org.pantsbuild:jarjar:1.7.2",
]

TINK_JAVA_AWSKMS_MAVEN_ARTIFACTS = [
    "com.amazonaws:aws-java-sdk-core:1.12.779",
    "com.amazonaws:aws-java-sdk-kms:1.12.779",
    "com.google.auto.service:auto-service-annotations:1.1.1",
    "com.google.auto.service:auto-service:1.1.1",
    "com.google.auto:auto-common:1.2.2",
    "com.google.code.findbugs:jsr305:3.0.2",
    "com.google.errorprone:error_prone_annotations:2.36.0",
    "com.google.guava:guava:33.3.1-jre",
]

def tink_java_awskms_deps():
    if not native.existing_rule("tink_java"):
        # Release from 2024-08-30.
        http_archive(
            name = "tink_java",
            urls = ["https://github.com/tink-crypto/tink-java/releases/download/v1.15.0/tink-java-1.15.0.zip"],
            strip_prefix = "tink-java-1.15.0",
            sha256 = "e246f848f7749e37f558955ecb50345b04d79ddb9d8d1e8ae19f61e8de530582",
        )
