from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class ImageRecord:
    id: str
    file: Path
    source_url: str
    creator: str
    license_name: str
    license_evidence: str
    verified_at: str
    share_allowed: bool
    sha256: str
    width: int
    height: int
    mime_type: str
    output_name: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "localFile": f"assets/{self.output_name}",
            "sha256": self.sha256,
            "width": self.width,
            "height": self.height,
            "mimeType": self.mime_type,
            "sourceUrl": self.source_url,
            "creator": self.creator,
            "license": self.license_name,
            "licenseEvidence": self.license_evidence,
            "verifiedAt": self.verified_at,
            "shareAllowed": self.share_allowed,
        }


@dataclass(frozen=True)
class CardRecord:
    source_file: Path
    payload: dict[str, Any]

    @property
    def id(self) -> str:
        return str(self.payload["id"])

    @property
    def revision(self) -> int:
        return int(self.payload["revision"])

    @property
    def status(self) -> str:
        return str(self.payload["status"])


@dataclass(frozen=True)
class ContentProject:
    schema_version: int
    content_version: str
    published_at: str
    minimum_app_version_code: int
    release_notes: str
    repository_owner: str
    repository_name: str


@dataclass(frozen=True)
class ValidatedContent:
    root: Path
    project: ContentProject
    cards: tuple[CardRecord, ...]
    images: tuple[ImageRecord, ...]

    @property
    def published_cards(self) -> tuple[CardRecord, ...]:
        return tuple(card for card in self.cards if card.status == "published")

    @property
    def withdrawals(self) -> tuple[CardRecord, ...]:
        return tuple(card for card in self.cards if card.status == "withdrawn")
