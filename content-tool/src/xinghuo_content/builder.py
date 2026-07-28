from __future__ import annotations

import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from typing import Any

from .models import ValidatedContent
from .validator import validate_content

ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


class BuildError(Exception):
    pass


def _json_bytes(payload: Any) -> bytes:
    return (
        json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def _write_deterministic_zip(source_dir: Path, destination: Path) -> None:
    with zipfile.ZipFile(
        destination,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as archive:
        for path in sorted(item for item in source_dir.rglob("*") if item.is_file()):
            relative = path.relative_to(source_dir).as_posix()
            info = zipfile.ZipInfo(relative, ZIP_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def _payloads(content: ValidatedContent) -> dict[str, Any]:
    cards = [card.payload for card in content.published_cards]
    cards.sort(key=lambda card: card["id"])
    images = [image.to_payload() for image in content.images]
    images.sort(key=lambda image: image["id"])
    withdrawals = [
        {
            "id": card.id,
            "revision": card.revision,
            "withdrawnAt": card.payload["reviewedAt"],
        }
        for card in content.withdrawals
    ]
    withdrawals.sort(key=lambda withdrawal: withdrawal["id"])
    project = content.project
    return {
        "package.json": {
            "schemaVersion": project.schema_version,
            "contentVersion": project.content_version,
            "publishedAt": project.published_at,
        },
        "cards.json": {"schemaVersion": project.schema_version, "cards": cards},
        "images.json": {"schemaVersion": project.schema_version, "images": images},
        "withdrawals.json": {
            "schemaVersion": project.schema_version,
            "withdrawals": withdrawals,
        },
    }


def build_package(
    content_root: Path,
    output_dir: Path,
    *,
    formal: bool = False,
    bootstrap_output: Path | None = None,
    verify_deterministic: bool = False,
) -> dict[str, Any]:
    content = validate_content(content_root, formal=formal)
    output_dir.mkdir(parents=True, exist_ok=True)
    payloads = _payloads(content)
    package_name = f"content-v{content.project.content_version}.zip"
    package_path = output_dir / package_name
    with tempfile.TemporaryDirectory(prefix=".xinghuo-build-", dir=output_dir) as temporary:
        staging = Path(temporary)
        for name, payload in payloads.items():
            (staging / name).write_bytes(_json_bytes(payload))
        assets_dir = staging / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)
        for image in content.images:
            shutil.copyfile(image.file, assets_dir / image.output_name)
        _write_deterministic_zip(staging, package_path)
    package_bytes = package_path.read_bytes()
    package_sha256 = hashlib.sha256(package_bytes).hexdigest()
    owner = content.project.repository_owner
    repository = content.project.repository_name
    release_url = (
        f"https://github.com/{owner}/{repository}/releases/download/"
        f"content-v{content.project.content_version}/{package_name}"
    )
    manifest = {
        "schemaVersion": 1,
        "contentVersion": content.project.content_version,
        "publishedAt": content.project.published_at,
        "minimumAppVersionCode": content.project.minimum_app_version_code,
        "packageUrl": release_url,
        "packageBytes": len(package_bytes),
        "packageSha256": package_sha256,
        "changes": {
            "added": len(content.published_cards),
            "updated": 0,
            "withdrawn": len(content.withdrawals),
        },
        "releaseNotes": content.project.release_notes,
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_bytes(_json_bytes(manifest))
    if bootstrap_output is not None:
        bootstrap_output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(package_path, bootstrap_output)
    result = {
        "package": str(package_path),
        "manifest": str(manifest_path),
        "sha256": package_sha256,
        "bytes": len(package_bytes),
        "cards": len(content.published_cards),
        "withdrawals": len(content.withdrawals),
    }
    if verify_deterministic:
        with tempfile.TemporaryDirectory(prefix="xinghuo-verify-") as temporary:
            verification = build_package(
                content_root,
                Path(temporary),
                formal=formal,
            )
            verification_manifest = Path(verification["manifest"]).read_bytes()
            if (
                verification["sha256"] != package_sha256
                or verification_manifest != manifest_path.read_bytes()
            ):
                raise BuildError("repeated builds produced different release assets")
        result["deterministicVerified"] = True
    return result
