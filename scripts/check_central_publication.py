#!/usr/bin/env python3
"""Validate Maven Central publication configuration and release artifacts."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

PUBLIC_MODULES = (
    "otryx-core",
    "otryx-codegen",
    "otryx-codec-fory",
    "otryx-transport-vertx",
    "otryx-registry-etcd",
    "otryx-registry-nacos",
    "otryx-proxy-cglib",
    "otryx-proxy-bytebuddy",
    "otryx-observability-micrometer",
    "otryx-observability-opentelemetry",
    "otryx-observability-jfr",
    "otryx-spring-boot-autoconfigure",
    "otryx-spring-boot-starter",
    "otryx-spring-boot-starter-lite",
)

NON_PUBLIC_ARTIFACTS = {
    "otryx-examples",
    "otryx-example-api",
    "otryx-example-provider",
    "otryx-example-consumer",
    "otryx-benchmarks",
}


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    raise SystemExit(1)


def required_text(root: ET.Element, path: str, label: str) -> str:
    value = root.findtext(path, namespaces=NS)
    if value is None or not value.strip():
        fail(f"Missing Maven Central POM metadata: {label}")
    return value.strip()


def profile(root: ET.Element, profile_id: str) -> ET.Element:
    for node in root.findall("m:profiles/m:profile", NS):
        if node.findtext("m:id", namespaces=NS) == profile_id:
            return node
    fail(f"Missing Maven profile: {profile_id}")
    raise AssertionError("unreachable")


def plugin(
    container: ET.Element,
    group_id: str,
    artifact_id: str,
) -> ET.Element:
    for node in container.findall(".//m:plugin", NS):
        if (
            node.findtext("m:groupId", namespaces=NS) == group_id
            and node.findtext("m:artifactId", namespaces=NS) == artifact_id
        ):
            return node
    fail(f"Missing Maven plugin: {group_id}:{artifact_id}")
    raise AssertionError("unreachable")


def check_metadata(root: ET.Element) -> None:
    required_text(root, "m:name", "name")
    required_text(root, "m:description", "description")
    required_text(root, "m:url", "url")
    required_text(root, "m:licenses/m:license/m:name", "license name")
    required_text(root, "m:licenses/m:license/m:url", "license URL")
    required_text(root, "m:developers/m:developer/m:name", "developer name")
    required_text(root, "m:developers/m:developer/m:url", "developer URL")
    required_text(root, "m:scm/m:connection", "SCM connection")
    required_text(root, "m:scm/m:developerConnection", "SCM developer connection")
    required_text(root, "m:scm/m:url", "SCM URL")


def check_profiles(root: ET.Element) -> None:
    central_version = required_text(
        root,
        "m:properties/m:central-publishing-maven-plugin.version",
        "central-publishing-maven-plugin.version",
    )
    if central_version != "0.11.0":
        fail(
            "Unexpected Central Publishing Maven Plugin version: "
            f"{central_version!r}"
        )

    gpg_version = required_text(
        root,
        "m:properties/m:maven-gpg-plugin.version",
        "maven-gpg-plugin.version",
    )
    if gpg_version != "3.2.8":
        fail(f"Unexpected Maven GPG Plugin version: {gpg_version!r}")

    release = profile(root, "release")
    javadoc = plugin(
        release,
        "org.apache.maven.plugins",
        "maven-javadoc-plugin",
    )
    if "jar" not in {
        goal.text
        for goal in javadoc.findall(
            "m:executions/m:execution/m:goals/m:goal",
            NS,
        )
    }:
        fail("release profile must attach Javadoc JARs")

    central = profile(root, "central-release")
    gpg = plugin(
        central,
        "org.apache.maven.plugins",
        "maven-gpg-plugin",
    )
    if gpg.findtext("m:version", namespaces=NS) != "${maven-gpg-plugin.version}":
        fail("central-release must use the managed Maven GPG Plugin version")
    gpg_execution = gpg.find("m:executions/m:execution", NS)
    if gpg_execution is None:
        fail("central-release must configure Maven GPG signing")
    if gpg_execution.findtext("m:phase", namespaces=NS) != "verify":
        fail("Maven GPG signing must run during verify")
    if "sign" not in {
        goal.text
        for goal in gpg_execution.findall("m:goals/m:goal", NS)
    }:
        fail("Maven GPG Plugin must execute the sign goal")

    publisher = plugin(
        central,
        "org.sonatype.central",
        "central-publishing-maven-plugin",
    )
    if (
        publisher.findtext("m:version", namespaces=NS)
        != "${central-publishing-maven-plugin.version}"
    ):
        fail("central-release must use the managed Central plugin version")
    if publisher.findtext("m:extensions", namespaces=NS) != "true":
        fail("Central Publishing Maven Plugin must be a Maven extension")

    configuration = publisher.find("m:configuration", NS)
    if configuration is None:
        fail("Central Publishing Maven Plugin configuration is missing")
    expected_values = {
        "publishingServerId": "central",
        "autoPublish": "${central.autoPublish}",
        "waitUntil": "${central.waitUntil}",
        "checksums": "all",
    }
    for key, expected in expected_values.items():
        actual = configuration.findtext(f"m:{key}", namespaces=NS)
        if actual != expected:
            fail(
                f"Unexpected Central plugin {key}: "
                f"{actual!r}, expected {expected!r}"
            )

    excluded = {
        node.text.strip()
        for node in configuration.findall(
            "m:excludeArtifacts/m:excludeArtifact",
            NS,
        )
        if node.text and node.text.strip()
    }
    missing = sorted(NON_PUBLIC_ARTIFACTS - excluded)
    if missing:
        fail(
            "Central publication must exclude non-public artifacts: "
            + ", ".join(missing)
        )



def check_dependency_only_starter() -> None:
    for module in (
        "otryx-spring-boot-starter",
        "otryx-spring-boot-starter-lite",
    ):
        starter_pom_path = ROOT / module / "pom.xml"
        starter = ET.parse(starter_pom_path).getroot()
        release = profile(starter, "release")

        source = plugin(
            release,
            "org.apache.maven.plugins",
            "maven-source-plugin",
        )
        if source.findtext(
            "m:configuration/m:skipSource",
            namespaces=NS,
        ) != "true":
            fail(
                "Dependency-only starter must skip standard source JAR "
                "generation in the release profile"
            )

        javadoc = plugin(
            release,
            "org.apache.maven.plugins",
            "maven-javadoc-plugin",
        )
        if javadoc.findtext(
            "m:configuration/m:skip",
            namespaces=NS,
        ) != "true":
            fail(
                "Dependency-only starter must skip standard Javadoc "
                "generation in the release profile"
            )

        jar = plugin(
            release,
            "org.apache.maven.plugins",
            "maven-jar-plugin",
        )
        classifiers = {
            node.text.strip()
            for node in jar.findall(
                "m:executions/m:execution/m:configuration/m:classifier",
                NS,
            )
            if node.text and node.text.strip()
        }
        if classifiers != {"sources", "javadoc"}:
            fail(
                "Dependency-only starter must attach placeholder "
                "sources and javadoc JARs"
            )

        placeholder = (
            ROOT
            / module
            / "src"
            / "central-placeholder"
            / "README.md"
        )
        if not placeholder.is_file():
            fail(
                "Missing Maven Central placeholder document for "
                "dependency-only starter"
            )

def check_starter_dependency_boundaries() -> None:
    def direct_artifacts(module: str) -> set[str]:
        pom = ET.parse(ROOT / module / "pom.xml").getroot()
        return {
            node.text.strip()
            for node in pom.findall(
                "m:dependencies/m:dependency/m:artifactId", NS
            )
            if node.text
        }

    full = direct_artifacts("otryx-spring-boot-starter")
    lite = direct_artifacts("otryx-spring-boot-starter-lite")
    auto = direct_artifacts("otryx-spring-boot-autoconfigure")
    optional = {
        "otryx-registry-etcd",
        "otryx-registry-nacos",
        "otryx-proxy-cglib",
        "otryx-proxy-bytebuddy",
    }
    if optional.intersection(lite) or optional.intersection(auto):
        fail("Minimal Starter / AutoConfiguration must not pull optional adapters")
    if not {
        "otryx-registry-etcd",
        "otryx-registry-nacos",
        "otryx-proxy-cglib",
    }.issubset(full):
        fail("Legacy full Starter must retain its original adapter capabilities")
    if not {
        "otryx-spring-boot-autoconfigure",
        "spring-boot-starter",
    }.issubset(lite):
        fail("Minimal Starter must provide auto-configuration and Boot starter")


def check_modules() -> None:
    for module in PUBLIC_MODULES:
        pom_path = ROOT / module / "pom.xml"
        if not pom_path.is_file():
            fail(f"Missing public module POM: {module}")
        pom = ET.parse(pom_path).getroot()
        artifact_id = required_text(
            pom,
            "m:artifactId",
            f"{module} artifactId",
        )
        if artifact_id != module:
            fail(
                f"Unexpected public artifactId for {module}: "
                f"{artifact_id!r}"
            )


def check_artifacts(version: str) -> None:
    for module in PUBLIC_MODULES:
        target = ROOT / module / "target"
        expected = (
            target / f"{module}-{version}.jar",
            target / f"{module}-{version}-sources.jar",
            target / f"{module}-{version}-javadoc.jar",
        )
        missing = [
            path.relative_to(ROOT)
            for path in expected
            if not path.is_file()
        ]
        if missing:
            fail(
                f"Missing Central release artifacts for {module}: "
                + ", ".join(str(path) for path in missing)
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version")
    parser.add_argument(
        "--require-artifacts",
        action="store_true",
        help="Verify main/source/Javadoc JARs for all public modules.",
    )
    args = parser.parse_args()

    if args.require_artifacts and not args.version:
        fail("--version is required with --require-artifacts")

    root = ET.parse(ROOT / "pom.xml").getroot()
    check_metadata(root)
    check_profiles(root)
    check_dependency_only_starter()
    check_starter_dependency_boundaries()
    check_modules()
    if args.require_artifacts:
        check_artifacts(args.version)

    print(
        "OTRYX RPC Maven Central publication checks passed"
        + (
            f": version={args.version}"
            if args.version
            else ""
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
