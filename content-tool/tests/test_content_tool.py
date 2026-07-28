from __future__ import annotations

import hashlib
import zipfile
from pathlib import Path

import pytest
import yaml
from PIL import Image

from xinghuo_content import (
    ValidationError,
    build_content_report,
    build_package,
    validate_content,
)


def _write_fixture(root: Path, *, quote: str = "实践是检验真理的标准。") -> None:
    (root / "cards").mkdir(parents=True)
    (root / "images").mkdir()
    (root / "project.yaml").write_text(
        yaml.safe_dump(
            {
                "schemaVersion": 2,
                "contentVersion": "1.1.0",
                "publishedAt": "2026-07-28T00:00:00Z",
                "minimumAppVersionCode": 2,
                "releaseNotes": "fixture",
                "repositoryOwner": "example",
                "repositoryName": "xinghuo",
            },
            allow_unicode=True,
        ),
        encoding="utf-8",
    )
    Image.new("RGB", (720, 720), "#ddd8cf").save(root / "images" / "paper.jpg")
    (root / "images" / "paper.yaml").write_text(
        yaml.safe_dump(
            {
                "id": "paper",
                "file": "paper.jpg",
                "sourceUrl": "https://example.com/image",
                "creator": "Fixture",
                "license": "CC0-1.0",
                "licenseEvidence": "https://creativecommons.org/publicdomain/zero/1.0/",
                "verifiedAt": "2026-07-28",
                "shareAllowed": True,
            },
            allow_unicode=True,
        ),
        encoding="utf-8",
    )
    (root / "cards" / "card.yaml").write_text(
        yaml.safe_dump(
            {
                "id": "b85d8407-3b74-4c5e-b516-b032a22d73aa",
                "revision": 1,
                "status": "published",
                "quote": quote,
                "literature": {
                    "series": "毛泽东选集",
                    "volume": "第一卷",
                    "workTitle": "实践论",
                    "authoredAt": "1937-07",
                },
                "themes": ["实践"],
                "interpretation": {
                    "coreMeaning": "这句话说明，认识是否可靠，不能只靠推论，而要回到实践中检验。",
                    "keyPoint": "这里说的实践不是一次偶然尝试，而是能反复接受事实检验的社会活动。",
                    "contemporaryRelevance": (
                        "面对具体问题，应先收集事实、形成判断，再根据结果修正原有看法。"
                    ),
                },
                "imageId": "paper",
                "sources": [
                    {
                        "name": "Source A",
                        "url": "https://example.com/a",
                        "accessedAt": "2026-07-28",
                        "type": "original",
                    },
                    {
                        "name": "Source B",
                        "url": "https://example.org/b",
                        "accessedAt": "2026-07-28",
                        "type": "authoritative",
                    },
                ],
                "review": {"status": "verified", "checkedAt": "2026-07-28"},
            },
            allow_unicode=True,
        ),
        encoding="utf-8",
    )


def test_validate_and_build_is_deterministic(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    validated = validate_content(root)
    assert len(validated.published_cards) == 1

    first = build_package(root, tmp_path / "first")
    second = build_package(root, tmp_path / "second")

    assert first["sha256"] == second["sha256"]
    assert hashlib.sha256(Path(first["package"]).read_bytes()).hexdigest() == first["sha256"]

    verified = build_package(root, tmp_path / "verified", verify_deterministic=True)
    assert verified["deterministicVerified"] is True


def test_quote_over_90_code_points_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root, quote="实" * 91)

    with pytest.raises(ValidationError, match="maximum is 90"):
        validate_content(root)


@pytest.mark.parametrize(
    ("mutate", "message"),
    [
        (lambda card: card.pop("interpretation"), "interpretation must be a mapping"),
        (
            lambda card: card["interpretation"].pop("coreMeaning"),
            "coreMeaning must be a non-empty string",
        ),
        (
            lambda card: card["interpretation"].update({"keyPoint": "   "}),
            "keyPoint must be a non-empty string",
        ),
        (
            lambda card: card["interpretation"].pop("contemporaryRelevance"),
            "contemporaryRelevance must be a non-empty string",
        ),
    ],
)
def test_published_card_requires_complete_interpretation(
    tmp_path: Path,
    mutate,
    message: str,
) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    mutate(card)
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match=message):
        validate_content(root)


def test_interpretation_over_600_code_points_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["interpretation"]["coreMeaning"] = "实" * 601
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(
        ValidationError,
        match="interpretation has 6[0-9]{2} code points; maximum is 600",
    ):
        validate_content(root)


def test_formal_content_requires_exactly_30_cards(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)

    with pytest.raises(ValidationError, match="exactly 30"):
        validate_content(root, formal=True)


def test_unknown_card_field_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["workTtile"] = "typo"
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="unknown fields"):
        validate_content(root)


def test_published_sources_must_use_independent_hosts(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["sources"][1]["url"] = "https://example.com/b"
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="at least two hosts"):
        validate_content(root)


def test_optional_reading_section_must_be_text(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["background"] = ["not", "text"]
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="background must be a string"):
        validate_content(root)


def test_package_contains_only_declared_files(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    output = tmp_path / "dist"
    result = build_package(root, output)

    with zipfile.ZipFile(result["package"]) as archive:
        names = set(archive.namelist())
        package_info = yaml.safe_load(archive.read("package.json"))
        cards = yaml.safe_load(archive.read("cards.json"))
        images = yaml.safe_load(archive.read("images.json"))
        withdrawals = yaml.safe_load(archive.read("withdrawals.json"))

    assert {"package.json", "cards.json", "images.json", "withdrawals.json"} < names
    assert len([name for name in names if name.startswith("assets/")]) == 1
    assert package_info["schemaVersion"] == 2
    assert cards["schemaVersion"] == 2
    assert images["schemaVersion"] == 2
    assert withdrawals["schemaVersion"] == 2
    assert cards["cards"][0]["interpretation"]["coreMeaning"]


def test_content_report_summarizes_review_dimensions(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    report = build_content_report(validate_content(root))

    assert report["publishedCards"] == 1
    assert report["themes"] == {"实践": 1}
    assert report["seriesAndVolumes"] == {"毛泽东选集 / 第一卷": 1}
    assert report["quoteLengths"]["maximum"] == len("实践是检验真理的标准。")
    assert report["interpretations"]["complete"] == 1
    assert report["interpretations"]["maximumCodePoints"] > 0
    assert report["sourceDomains"] == {"example.com": 1, "example.org": 1}
    assert report["images"][0]["uses"] == 1
