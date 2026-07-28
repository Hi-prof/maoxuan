from __future__ import annotations

import json
from collections import Counter
from collections.abc import Iterable
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from .models import ValidatedContent


def _counts(values: Iterable[str]) -> dict[str, int]:
    return dict(sorted(Counter(values).items()))


def build_content_report(content: ValidatedContent) -> dict[str, Any]:
    cards = sorted(content.published_cards, key=lambda card: card.id)
    image_usage = Counter(str(card.payload["imageId"]) for card in cards)
    source_domains = _counts(
        (urlparse(source["url"]).hostname or "").lower().removeprefix("www.")
        for card in cards
        for source in card.payload["sources"]
    )
    quote_lengths = [len(str(card.payload["quote"])) for card in cards]
    interpretation_lengths = [
        sum(len(str(value)) for value in card.payload["interpretation"].values())
        for card in cards
    ]
    images = sorted(content.images, key=lambda image: image.id)
    return {
        "contentVersion": content.project.content_version,
        "publishedCards": len(cards),
        "themes": _counts(theme for card in cards for theme in card.payload["themes"]),
        "seriesAndVolumes": _counts(
            f"{card.payload['series']} / {card.payload['volume']}" for card in cards
        ),
        "readingSections": {
            "withContext": sum(bool(card.payload["contextExcerpt"]) for card in cards),
            "withBackground": sum(bool(card.payload["background"]) for card in cards),
            "withStory": sum(bool(card.payload["story"]) for card in cards),
        },
        "quoteLengths": {
            "minimum": min(quote_lengths, default=0),
            "maximum": max(quote_lengths, default=0),
            "average": round(sum(quote_lengths) / len(quote_lengths), 2)
            if quote_lengths
            else 0,
            "cards": [
                {
                    "id": card.id,
                    "workTitle": card.payload["workTitle"],
                    "codePoints": len(str(card.payload["quote"])),
                }
                for card in cards
            ],
        },
        "interpretations": {
            "complete": sum(
                all(card.payload["interpretation"].values()) for card in cards
            ),
            "minimumCodePoints": min(interpretation_lengths, default=0),
            "maximumCodePoints": max(interpretation_lengths, default=0),
            "averageCodePoints": round(
                sum(interpretation_lengths) / len(interpretation_lengths), 2
            )
            if interpretation_lengths
            else 0,
        },
        "sourceDomains": source_domains,
        "images": [
            {
                "id": image.id,
                "uses": image_usage[image.id],
                "creator": image.creator,
                "license": image.license_name,
                "shareAllowed": image.share_allowed,
                "sha256": image.sha256,
            }
            for image in images
        ],
    }


def write_content_report(content: ValidatedContent, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            build_content_report(content),
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
