workspace(name = "tink_java_awskms")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "tink_java",
    urls = ["https://github.com/tink-crypto/tink-java/archive/refs/tags/v1.10.0.zip"],
    strip_prefix = "tink-java-1.10.0",
    sha256 = "9a7d8a639f1a2642af032e43ec217ad57a603be31a52a4e35a39c743a6c2e8e8",
)

load("@tink_java//:tink_java_deps.bzl", "TINK_MAVEN_ARTIFACTS", "tink_java_deps")

tink_java_deps()

load("@tink_java//:tink_java_deps_init.bzl", "tink_java_deps_init")

tink_java_deps_init()

load("@tink_java_awskms//:tink_java_awskms_deps.bzl", "TINK_JAVA_AWSKMS_MAVEN_ARTIFACTS")

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = TINK_MAVEN_ARTIFACTS + TINK_JAVA_AWSKMS_MAVEN_ARTIFACTS,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)
