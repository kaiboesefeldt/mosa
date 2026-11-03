#!/usr/bin/env python3
"""Refresh the checked-in client notices from the resolved production npm graph."""

import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NPM_ROOT = ROOT / "build" / "js"
NODE_MODULES = NPM_ROOT / "node_modules"
OUTPUT = ROOT / "client" / "src" / "jsMain" / "resources" / "licenses" / "THIRD-PARTY-NOTICES.md"


def collect_dependencies(node, result):
    for name, dependency in node.get("dependencies", {}).items():
        version = dependency.get("version")
        if version:
            result.add((name, version))
        collect_dependencies(dependency, result)


def license_text_files(package_dir):
    return sorted(
        path
        for path in package_dir.iterdir()
        if path.is_file()
        and any(term in path.name.lower() for term in ("license", "notice", "copying"))
        and (path.suffix.lower() in ("", ".txt", ".md") or path.suffix.lower() == ".license")
    )


def main():
    process = subprocess.run(
        ["npm", "ls", "--workspace=mosa-client", "--all", "--omit=dev", "--json"],
        cwd=NPM_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    tree = json.loads(process.stdout)
    dependencies = set()
    collect_dependencies(tree, dependencies)
    dependencies = {package for package in dependencies if not package[0].startswith("mosa-")}

    packages = {}
    for manifest in NODE_MODULES.rglob("package.json"):
        metadata = json.loads(manifest.read_text())
        key = metadata.get("name"), metadata.get("version")
        if key in dependencies:
            packages[key] = (manifest.parent, metadata)

    missing = dependencies - packages.keys()
    if missing:
        raise RuntimeError(f"Could not locate resolved npm packages: {sorted(missing)}")

    lines = [
        "# Client third-party notices",
        "",
        "Generated from the resolved production npm dependency graph for the `mosa-client` workspace. "
        "This list excludes development and test dependencies.",
        "The server runtime dependency inventory is displayed in the same legal dialog.",
        "",
        "## Runtime-fetched model",
        "",
        "The server downloads [`intfloat/multilingual-e5-small`](https://huggingface.co/intfloat/multilingual-e5-small) when embeddings are first needed. Its model card declares the model weights MIT-licensed. The model is fetched at runtime and is not bundled in this client notice file.",
        "",
        "| Package | Version | Declared license |",
        "| --- | --- | --- |",
    ]

    def license_label(value):
        if isinstance(value, dict):
            return value.get("type", "license metadata unavailable")
        return value or "license metadata unavailable"

    ordered = sorted(packages.items())
    for (name, version), (_, metadata) in ordered:
        lines.append(f"| `{name}` | {version} | {license_label(metadata.get('license'))} |")

    lines += [
        "",
        "## Kotlin and Gradle supplied client runtime",
        "",
        "These Kotlin/JS runtime and wrapper modules are distributed under Apache License 2.0:",
        "",
        "| Component | Version |",
        "| --- | --- |",
        "| Kotlin standard library | 2.4.21 |",
        "| Ktor JS client and serialization | 3.6.1 |",
        "| Koin core | 4.2.2 |",
        "| kotlinx.coroutines | 1.11.0 |",
        "| kotlinx.serialization | 1.12.0 |",
        "| Kotlin React wrappers | 2026.11.0-19.3.1 |",
        "| Kotlin browser wrappers | 2026.11.0 |",
        "| kotlinx-datetime | 0.8.0-0.6.x-compat |",
        "",
        "The Apache License 2.0 text is included as [`Apache-2.0.txt`](Apache-2.0.txt).",
        "",
        "## License and notice texts",
        "",
    ]
    for (name, version), (package_dir, metadata) in ordered:
        texts = license_text_files(package_dir)
        lines += [f"### `{name}` {version}", "", f"Declared license: {license_label(metadata.get('license'))}.", ""]
        if not texts:
            lines += ["No license text file was included in this npm package. The package license metadata above is retained for review.", ""]
            continue
        for path in texts:
            license_text = "\n".join(line.rstrip() for line in path.read_text(errors="replace").splitlines()).rstrip()
            lines += [f"#### `{path.name}`", "", "```text", license_text, "```", ""]

    OUTPUT.write_text("\n".join(lines).rstrip() + "\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)} with {len(packages)} resolved packages.")


if __name__ == "__main__":
    main()
