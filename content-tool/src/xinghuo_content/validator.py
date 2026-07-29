from __future__ import annotations

import hashlib
import re
import unicodedata
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import yaml
from PIL import Image

from .models import CardRecord, ContentProject, ImageRecord, ValidatedContent

SEMVER_RE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
PARTIAL_DATE_RE = re.compile(r"^\d{4}(?:-\d{2}(?:-\d{2})?)?$")
ALLOWED_CARD_STATUSES = {"draft", "published", "withdrawn"}
ALLOWED_EVIDENCE_TYPES = {"original", "authoritative", "contextual"}
ALLOWED_IMAGE_SUFFIXES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}
PROJECT_FIELDS = {
    "schemaVersion",
    "contentVersion",
    "publishedAt",
    "minimumAppVersionCode",
    "expectedPublishedCards",
    "releaseNotes",
    "repositoryOwner",
    "repositoryName",
}
IMAGE_FIELDS = {
    "id",
    "file",
    "sourceUrl",
    "creator",
    "license",
    "licenseEvidence",
    "verifiedAt",
    "shareAllowed",
}
CARD_FIELDS = {
    "id",
    "revision",
    "status",
    "quote",
    "literature",
    "themes",
    "interpretation",
    "historicalEvent",
    "background",
    "story",
    "imageId",
    "sources",
    "review",
}
LITERATURE_FIELDS = {"series", "volume", "workTitle", "authoredAt"}
INTERPRETATION_FIELDS = {"inspiration", "explanation"}
SOURCE_FIELDS = {"name", "url", "accessedAt", "type"}
REVIEW_FIELDS = {"status", "checkedAt"}
IMAGE_ID_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$")


class ValidationError(Exception):
    def __init__(self, issues: list[str]) -> None:
        self.issues = issues
        super().__init__("\n".join(issues))


def _load_yaml(path: Path, issues: list[str]) -> dict[str, Any] | None:
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        issues.append(f"{path}: cannot read YAML: {exc}")
        return None
    if not isinstance(data, dict):
        issues.append(f"{path}: root value must be a mapping")
        return None
    return data


def _require_text(data: dict[str, Any], field: str, path: Path, issues: list[str]) -> str:
    value = data.get(field)
    if not isinstance(value, str) or not value.strip():
        issues.append(f"{path}: {field} must be a non-empty string")
        return ""
    return value.strip()


def _optional_text(
    data: dict[str, Any], field: str, path: Path, issues: list[str]
) -> str | None:
    value = data.get(field)
    if value is None:
        return None
    if not isinstance(value, str):
        issues.append(f"{path}: {field} must be a string when provided")
        return None
    value = value.strip()
    return value or None


def _reject_unknown_fields(
    data: dict[str, Any], allowed: set[str], path: Path, issues: list[str], prefix: str = ""
) -> None:
    unknown = sorted(set(data) - allowed)
    if unknown:
        location = f"{prefix} " if prefix else ""
        issues.append(f"{path}: {location}contains unknown fields: {unknown}")


def _is_http_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def _source_host(value: str) -> str:
    hostname = (urlparse(value).hostname or "").lower()
    return hostname.removeprefix("www.")


def _validate_project(root: Path, issues: list[str]) -> ContentProject | None:
    path = root / "project.yaml"
    data = _load_yaml(path, issues)
    if data is None:
        return None
    _reject_unknown_fields(data, PROJECT_FIELDS, path, issues)
    schema_version = data.get("schemaVersion")
    if schema_version != 3:
        issues.append(f"{path}: schemaVersion must be 3")
    content_version = _require_text(data, "contentVersion", path, issues)
    if content_version and not SEMVER_RE.fullmatch(content_version):
        issues.append(f"{path}: contentVersion must use MAJOR.MINOR.PATCH")
    published_at = _require_text(data, "publishedAt", path, issues)
    if published_at:
        try:
            parsed_published_at = datetime.fromisoformat(published_at.replace("Z", "+00:00"))
            if (
                parsed_published_at.tzinfo is None
                or parsed_published_at.utcoffset() != UTC.utcoffset(None)
            ):
                raise ValueError("timestamp must use UTC")
        except ValueError:
            issues.append(f"{path}: publishedAt must be a UTC ISO-8601 timestamp")
    minimum_app_version_code = data.get("minimumAppVersionCode")
    if not isinstance(minimum_app_version_code, int) or minimum_app_version_code < 1:
        issues.append(f"{path}: minimumAppVersionCode must be a positive integer")
        minimum_app_version_code = 1
    expected_published_cards = data.get("expectedPublishedCards")
    if type(expected_published_cards) is not int or expected_published_cards < 1:
        issues.append(f"{path}: expectedPublishedCards must be a positive integer")
        expected_published_cards = 1
    release_notes = _require_text(data, "releaseNotes", path, issues)
    repository_owner = _require_text(data, "repositoryOwner", path, issues)
    repository_name = _require_text(data, "repositoryName", path, issues)
    return ContentProject(
        schema_version=3,
        content_version=content_version,
        published_at=published_at,
        minimum_app_version_code=minimum_app_version_code,
        expected_published_cards=expected_published_cards,
        release_notes=release_notes,
        repository_owner=repository_owner,
        repository_name=repository_name,
    )


def _validate_image(path: Path, root: Path, issues: list[str]) -> ImageRecord | None:
    data = _load_yaml(path, issues)
    if data is None:
        return None
    _reject_unknown_fields(data, IMAGE_FIELDS, path, issues)
    image_id = _require_text(data, "id", path, issues)
    if image_id and not IMAGE_ID_RE.fullmatch(image_id):
        issues.append(f"{path}: id must use lowercase letters, digits, and hyphens")
    relative_file = _require_text(data, "file", path, issues)
    source_url = _require_text(data, "sourceUrl", path, issues)
    creator = _require_text(data, "creator", path, issues)
    license_name = _require_text(data, "license", path, issues)
    license_evidence = _require_text(data, "licenseEvidence", path, issues)
    verified_at = _require_text(data, "verifiedAt", path, issues)
    share_allowed = data.get("shareAllowed")
    if share_allowed is not True:
        issues.append(f"{path}: shareAllowed must be true for formal image assets")
    if source_url and not _is_http_url(source_url):
        issues.append(f"{path}: sourceUrl must be an HTTP(S) URL")
    if license_evidence and not _is_http_url(license_evidence):
        issues.append(f"{path}: licenseEvidence must be an HTTP(S) URL")
    if verified_at and not DATE_RE.fullmatch(verified_at):
        issues.append(f"{path}: verifiedAt must use YYYY-MM-DD")

    file_path = (root / "images" / relative_file).resolve()
    images_root = (root / "images").resolve()
    if images_root not in file_path.parents:
        issues.append(f"{path}: file must stay inside content/images")
        return None
    suffix = file_path.suffix.lower()
    mime_type = ALLOWED_IMAGE_SUFFIXES.get(suffix)
    if mime_type is None:
        issues.append(f"{path}: unsupported image extension {suffix}")
        return None
    if not file_path.is_file():
        issues.append(f"{path}: image file does not exist: {relative_file}")
        return None
    if file_path.stat().st_size > 5 * 1024 * 1024:
        issues.append(f"{path}: image file exceeds 5 MiB")
    try:
        with Image.open(file_path) as image:
            image.verify()
        with Image.open(file_path) as image:
            width, height = image.size
    except (OSError, ValueError) as exc:
        issues.append(f"{path}: cannot decode image: {exc}")
        return None
    if width < 720 or height < 720:
        issues.append(f"{path}: image must be at least 720 x 720 pixels")
    if width > 8192 or height > 8192 or width * height > 40_000_000:
        issues.append(f"{path}: image dimensions exceed the decode safety limit")
    sha256 = hashlib.sha256(file_path.read_bytes()).hexdigest()
    return ImageRecord(
        id=image_id,
        file=file_path,
        source_url=source_url,
        creator=creator,
        license_name=license_name,
        license_evidence=license_evidence,
        verified_at=verified_at,
        share_allowed=share_allowed is True,
        sha256=sha256,
        width=width,
        height=height,
        mime_type=mime_type,
        output_name=f"{sha256}{suffix}",
    )


def _validate_source(raw: Any, index: int, path: Path, issues: list[str]) -> dict[str, str]:
    if not isinstance(raw, dict):
        issues.append(f"{path}: sources[{index}] must be a mapping")
        return {"name": "", "url": "", "accessedAt": "", "type": "contextual"}
    _reject_unknown_fields(raw, SOURCE_FIELDS, path, issues, f"sources[{index}]")
    name = _require_text(raw, "name", path, issues)
    url = _require_text(raw, "url", path, issues)
    accessed_at = _require_text(raw, "accessedAt", path, issues)
    evidence_type = _require_text(raw, "type", path, issues)
    if url and not _is_http_url(url):
        issues.append(f"{path}: sources[{index}].url must be HTTP(S)")
    if accessed_at and not DATE_RE.fullmatch(accessed_at):
        issues.append(f"{path}: sources[{index}].accessedAt must use YYYY-MM-DD")
    if evidence_type not in ALLOWED_EVIDENCE_TYPES:
        issues.append(
            f"{path}: sources[{index}].type must be one of {sorted(ALLOWED_EVIDENCE_TYPES)}"
        )
    return {"name": name, "url": url, "accessedAt": accessed_at, "type": evidence_type}


def _validate_interpretation(
    raw: Any,
    *,
    required: bool,
    path: Path,
    issues: list[str],
) -> dict[str, str]:
    if not isinstance(raw, dict):
        if required:
            issues.append(f"{path}: interpretation must be a mapping")
        return {"inspiration": "", "explanation": ""}

    _reject_unknown_fields(raw, INTERPRETATION_FIELDS, path, issues, "interpretation")
    interpretation = {
        field: unicodedata.normalize("NFC", _require_text(raw, field, path, issues))
        for field in ("inspiration", "explanation")
    }
    if len(interpretation["inspiration"]) > 220:
        issues.append(
            f"{path}: interpretation.inspiration has "
            f"{len(interpretation['inspiration'])} code points; maximum is 220"
        )
    if len(interpretation["explanation"]) > 420:
        issues.append(
            f"{path}: interpretation.explanation has "
            f"{len(interpretation['explanation'])} code points; maximum is 420"
        )
    code_points = sum(len(text) for text in interpretation.values())
    if code_points > 600:
        issues.append(
            f"{path}: interpretation has {code_points} code points; maximum is 600"
        )
    return interpretation


def _validate_card(path: Path, issues: list[str]) -> CardRecord | None:
    data = _load_yaml(path, issues)
    if data is None:
        return None
    _reject_unknown_fields(data, CARD_FIELDS, path, issues)
    card_id = _require_text(data, "id", path, issues)
    try:
        uuid.UUID(card_id)
    except (ValueError, AttributeError):
        issues.append(f"{path}: id must be a UUID")
    revision = data.get("revision")
    if not isinstance(revision, int) or revision < 1:
        issues.append(f"{path}: revision must be a positive integer")
        revision = 1
    status = _require_text(data, "status", path, issues)
    if status not in ALLOWED_CARD_STATUSES:
        issues.append(f"{path}: status must be one of {sorted(ALLOWED_CARD_STATUSES)}")

    quote = unicodedata.normalize("NFC", _require_text(data, "quote", path, issues))
    if "\n" in quote or "\r" in quote:
        issues.append(f"{path}: quote must be a single paragraph")
    if len(quote) > 90:
        issues.append(f"{path}: quote has {len(quote)} code points; maximum is 90")

    literature = data.get("literature")
    if not isinstance(literature, dict):
        issues.append(f"{path}: literature must be a mapping")
        literature = {}
    else:
        _reject_unknown_fields(literature, LITERATURE_FIELDS, path, issues, "literature")
    series = _require_text(literature, "series", path, issues)
    volume = _require_text(literature, "volume", path, issues)
    work_title = _require_text(literature, "workTitle", path, issues)
    authored_at = _require_text(literature, "authoredAt", path, issues)
    if authored_at and not PARTIAL_DATE_RE.fullmatch(authored_at):
        issues.append(f"{path}: literature.authoredAt must use YYYY, YYYY-MM, or YYYY-MM-DD")

    themes = data.get("themes")
    if not isinstance(themes, list) or not themes or not all(
        isinstance(item, str) and item.strip() for item in themes
    ):
        issues.append(f"{path}: themes must contain at least one non-empty string")
        themes = []
    normalized_themes = [str(item).strip() for item in themes]
    if len(normalized_themes) != len(set(normalized_themes)):
        issues.append(f"{path}: themes must not contain duplicates")

    interpretation = _validate_interpretation(
        data.get("interpretation"),
        required=status == "published",
        path=path,
        issues=issues,
    )

    historical_event = unicodedata.normalize(
        "NFC", _require_text(data, "historicalEvent", path, issues)
    )
    if "\n" in historical_event or "\r" in historical_event:
        issues.append(f"{path}: historicalEvent must be a single paragraph")
    if len(historical_event) > 100:
        issues.append(
            f"{path}: historicalEvent has {len(historical_event)} code points; maximum is 100"
        )

    if status == "published":
        background = unicodedata.normalize(
            "NFC", _require_text(data, "background", path, issues)
        )
        story = unicodedata.normalize("NFC", _require_text(data, "story", path, issues))
    else:
        background = _optional_text(data, "background", path, issues)
        story = _optional_text(data, "story", path, issues)

    image_id = _require_text(data, "imageId", path, issues)
    raw_sources = data.get("sources")
    if not isinstance(raw_sources, list):
        issues.append(f"{path}: sources must be a list")
        raw_sources = []
    sources = [_validate_source(raw, index, path, issues) for index, raw in enumerate(raw_sources)]
    if status == "published" and len(sources) < 2:
        issues.append(f"{path}: published cards require at least two sources")
    if status == "published" and not any(
        source["type"] in {"original", "authoritative"} for source in sources
    ):
        issues.append(f"{path}: at least one source must be original or authoritative")
    urls = [source["url"] for source in sources if source["url"]]
    if len(urls) != len(set(urls)):
        issues.append(f"{path}: source URLs must be distinct")
    source_hosts = {_source_host(url) for url in urls}
    if status == "published" and len(source_hosts) < 2:
        issues.append(f"{path}: published cards require sources from at least two hosts")

    review = data.get("review")
    if not isinstance(review, dict):
        issues.append(f"{path}: review must be a mapping")
        review = {}
    else:
        _reject_unknown_fields(review, REVIEW_FIELDS, path, issues, "review")
    review_status = _require_text(review, "status", path, issues)
    checked_at = _require_text(review, "checkedAt", path, issues)
    if checked_at and not DATE_RE.fullmatch(checked_at):
        issues.append(f"{path}: review.checkedAt must use YYYY-MM-DD")
    if status == "published" and review_status != "verified":
        issues.append(f"{path}: published cards require review.status=verified")

    payload = {
        "id": card_id,
        "revision": revision,
        "status": status,
        "quote": quote,
        "series": series,
        "volume": volume,
        "workTitle": work_title,
        "authoredAt": authored_at,
        "themes": normalized_themes,
        "interpretation": interpretation,
        "historicalEvent": historical_event,
        "background": background,
        "story": story,
        "imageId": image_id,
        "sources": sources,
        "reviewedAt": checked_at,
    }
    return CardRecord(source_file=path, payload=payload)


def validate_content(root: Path, *, formal: bool = False) -> ValidatedContent:
    root = root.resolve()
    issues: list[str] = []
    project = _validate_project(root, issues)
    image_records = [
        record
        for path in sorted((root / "images").glob("*.yaml"))
        if (record := _validate_image(path, root, issues)) is not None
    ]
    card_records = [
        record
        for path in sorted((root / "cards").glob("*.yaml"))
        if (record := _validate_card(path, issues)) is not None
    ]

    image_ids = [image.id for image in image_records]
    duplicate_images = sorted({item for item in image_ids if image_ids.count(item) > 1})
    if duplicate_images:
        issues.append(f"duplicate image IDs: {duplicate_images}")
    card_ids = [card.id for card in card_records]
    duplicate_cards = sorted({item for item in card_ids if card_ids.count(item) > 1})
    if duplicate_cards:
        issues.append(f"duplicate card IDs: {duplicate_cards}")
    quote_sources: dict[str, list[Path]] = {}
    for card in card_records:
        if card.status == "published":
            quote = unicodedata.normalize("NFC", str(card.payload["quote"]))
            quote_sources.setdefault(quote, []).append(card.source_file)
    for quote, source_files in sorted(quote_sources.items()):
        if len(source_files) > 1:
            files = ", ".join(str(path) for path in sorted(source_files))
            issues.append(f"duplicate published quote {quote!r}: {files}")
    known_images = set(image_ids)
    for card in card_records:
        if card.status == "published" and card.payload["imageId"] not in known_images:
            issues.append(
                f"{card.source_file}: imageId {card.payload['imageId']!r} does not exist"
            )
    referenced_images = {
        str(card.payload["imageId"])
        for card in card_records
        if card.status == "published"
    }
    unused_images = sorted(known_images - referenced_images)
    if unused_images:
        issues.append(f"unreferenced image IDs: {unused_images}")
    published_count = sum(card.status == "published" for card in card_records)
    if (
        formal
        and project is not None
        and published_count != project.expected_published_cards
    ):
        issues.append(
            "expectedPublishedCards requires exactly "
            f"{project.expected_published_cards} published cards, got {published_count}"
        )
    if project is None:
        issues.append(f"{root / 'project.yaml'}: project metadata is required")
    if issues:
        raise ValidationError(issues)
    assert project is not None
    return ValidatedContent(
        root=root,
        project=project,
        cards=tuple(card_records),
        images=tuple(image_records),
    )
