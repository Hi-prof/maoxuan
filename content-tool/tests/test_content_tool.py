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
                "schemaVersion": 4,
                "contentVersion": "1.3.0",
                "publishedAt": "2026-07-28T00:00:00Z",
                "minimumAppVersionCode": 4,
                "expectedPublishedCards": 1,
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
                    "inspiration": (
                        "面对具体问题，应先收集事实、形成判断，再根据结果修正原有看法。"
                    ),
                    "explanation": (
                        "这句话说明，认识是否可靠，不能只靠推论，而要回到实践中检验。"
                        "这里说的实践不是一次偶然尝试，而是能反复接受事实检验的社会活动。"
                    ),
                },
                "historicalEvent": "1937年7月，毛泽东在延安讲授哲学问题。",
                "background": "抗日战争全面爆发前后，认识与实践问题受到集中讨论。",
                "story": "这次讲授的内容后来整理为《实践论》。",
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
            lambda card: card["interpretation"].pop("inspiration"),
            "inspiration must be a non-empty string",
        ),
        (
            lambda card: card["interpretation"].update({"explanation": "   "}),
            "explanation must be a non-empty string",
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
    card["interpretation"]["inspiration"] = "实" * 220
    card["interpretation"]["explanation"] = "践" * 381
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(
        ValidationError,
        match="interpretation has 6[0-9]{2} code points; maximum is 600",
    ):
        validate_content(root)


@pytest.mark.parametrize(
    ("field", "length", "maximum"),
    [("inspiration", 221, 220), ("explanation", 421, 420)],
)
def test_interpretation_section_limit_is_enforced(
    tmp_path: Path,
    field: str,
    length: int,
    maximum: int,
) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["interpretation"][field] = "实" * length
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(
        ValidationError,
        match=rf"interpretation\.{field} has {length} code points; maximum is {maximum}",
    ):
        validate_content(root)


def test_published_card_requires_background_sections(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["historicalEvent"] = "实" * 101
    card["background"] = "   "
    card.pop("story")
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError) as caught:
        validate_content(root)

    message = str(caught.value)
    assert "historicalEvent has 101 code points; maximum is 100" in message
    assert "background must be a non-empty string" in message
    assert "story must be a non-empty string" in message


def test_context_excerpt_is_rejected_as_unknown(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["contextExcerpt"] = "不再进入内容协议。"
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="unknown fields.*contextExcerpt"):
        validate_content(root)


def test_formal_content_accepts_declared_published_count(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)

    validated = validate_content(root, formal=True)

    assert len(validated.published_cards) == 1


def test_authored_at_accepts_bce_century_label(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["literature"]["authoredAt"] = "前5世纪"
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    validated = validate_content(root, formal=True)

    assert validated.published_cards[0].payload["authoredAt"] == "前5世纪"


@pytest.mark.parametrize("value", [None, 0, -1, True, "1"])
def test_expected_published_cards_must_be_a_positive_integer(
    tmp_path: Path,
    value: object,
) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    project_path = root / "project.yaml"
    project = yaml.safe_load(project_path.read_text(encoding="utf-8"))
    project["expectedPublishedCards"] = value
    project_path.write_text(
        yaml.safe_dump(project, allow_unicode=True),
        encoding="utf-8",
    )

    with pytest.raises(
        ValidationError,
        match="expectedPublishedCards must be a positive integer",
    ):
        validate_content(root)


def test_formal_content_rejects_fewer_cards_than_declared(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    project_path = root / "project.yaml"
    project = yaml.safe_load(project_path.read_text(encoding="utf-8"))
    project["expectedPublishedCards"] = 2
    project_path.write_text(
        yaml.safe_dump(project, allow_unicode=True),
        encoding="utf-8",
    )

    with pytest.raises(
        ValidationError,
        match="expectedPublishedCards requires exactly 2 published cards, got 1",
    ):
        validate_content(root, formal=True)


def test_formal_content_rejects_more_cards_than_declared(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    first_path = root / "cards" / "card.yaml"
    second = yaml.safe_load(first_path.read_text(encoding="utf-8"))
    second["id"] = "5b6cb1c2-01ad-4aee-933b-25bb21c40777"
    (root / "cards" / "second.yaml").write_text(
        yaml.safe_dump(second, allow_unicode=True),
        encoding="utf-8",
    )

    with pytest.raises(
        ValidationError,
        match="expectedPublishedCards requires exactly 1 published cards, got 2",
    ):
        validate_content(root, formal=True)


def test_duplicate_published_quote_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    first_path = root / "cards" / "card.yaml"
    second = yaml.safe_load(first_path.read_text(encoding="utf-8"))
    second["id"] = "5b6cb1c2-01ad-4aee-933b-25bb21c40777"
    second_path = root / "cards" / "second.yaml"
    second_path.write_text(
        yaml.safe_dump(second, allow_unicode=True),
        encoding="utf-8",
    )

    with pytest.raises(ValidationError) as caught:
        validate_content(root)

    message = str(caught.value)
    assert "duplicate published quote" in message
    assert str(first_path.resolve()) in message
    assert str(second_path.resolve()) in message


def test_unknown_card_field_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["workTtile"] = "typo"
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="unknown fields"):
        validate_content(root)


def test_schema_three_is_rejected(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "project.yaml"
    project = yaml.safe_load(path.read_text(encoding="utf-8"))
    project["schemaVersion"] = 3
    path.write_text(yaml.safe_dump(project, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="schemaVersion must be 4"):
        validate_content(root)


def test_published_card_accepts_single_strong_source(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["sources"] = card["sources"][:1]
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    validated = validate_content(root)

    assert len(validated.published_cards[0].payload["sources"]) == 1


def test_published_card_requires_at_least_one_source(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["sources"] = []
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="at least one source"):
        validate_content(root)


def test_published_card_requires_strong_source(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["sources"] = [
        {
            "name": "Context",
            "url": "https://example.com/context",
            "accessedAt": "2026-07-28",
            "type": "contextual",
        }
    ]
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="original or authoritative"):
        validate_content(root)


def test_required_background_must_be_non_empty_text(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    path = root / "cards" / "card.yaml"
    card = yaml.safe_load(path.read_text(encoding="utf-8"))
    card["background"] = ["not", "text"]
    path.write_text(yaml.safe_dump(card, allow_unicode=True), encoding="utf-8")

    with pytest.raises(ValidationError, match="background must be a non-empty string"):
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
    assert package_info["schemaVersion"] == 4
    assert cards["schemaVersion"] == 4
    assert images["schemaVersion"] == 4
    assert withdrawals["schemaVersion"] == 4
    assert cards["cards"][0]["interpretation"]["inspiration"]
    assert cards["cards"][0]["historicalEvent"]
    assert "contextExcerpt" not in cards["cards"][0]


def test_content_report_summarizes_review_dimensions(tmp_path: Path) -> None:
    root = tmp_path / "content"
    _write_fixture(root)
    report = build_content_report(validate_content(root))

    assert report["publishedCards"] == 1
    assert report["themes"] == {"实践": 1}
    assert report["seriesAndVolumes"] == {"毛泽东选集 / 第一卷": 1}
    assert report["workTitles"] == {"实践论": 1}
    assert report["quoteLengths"]["maximum"] == len("实践是检验真理的标准。")
    assert report["interpretations"]["complete"] == 1
    assert report["interpretations"]["maximumCodePoints"] > 0
    assert report["interpretations"]["inspiration"]["maximum"] > 0
    assert report["interpretations"]["explanation"]["maximum"] > 0
    assert report["readingSections"]["withHistoricalEvent"] == 1
    assert report["sourceDomains"] == {"example.com": 1, "example.org": 1}
    assert report["images"][0]["uses"] == 1
