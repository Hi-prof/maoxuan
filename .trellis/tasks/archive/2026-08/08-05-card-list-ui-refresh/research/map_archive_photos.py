from __future__ import annotations

import hashlib
import re
from collections import Counter, defaultdict
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parents[3]
CARD_DIR = PROJECT_ROOT / "content" / "cards"

SERIES_RULES = {
    "毛泽东选集": (
        (
            "archive-mao-selection-survey-masses",
            ("调查", "群众", "农民", "人民", "社会调查", "群众路线", "群众生活", "群众联系"),
        ),
        (
            "archive-mao-selection-war-army",
            ("战争", "军队", "军事", "武装", "战略", "抗战", "人民战争", "主动权"),
        ),
        (
            "archive-mao-selection-yanan-study",
            ("学习", "理论", "思想", "作风", "整风", "学风", "文风", "写作", "文化", "教育"),
        ),
        (
            "archive-mao-selection-production",
            ("经济", "生产", "建设", "工业", "商业", "自力更生", "工作重点"),
        ),
        (
            "archive-mao-selection-international",
            ("国际", "统一", "联合", "团结", "世界", "开放视野", "独立自主", "朋友"),
        ),
        (
            "archive-mao-selection-victory-city",
            ("胜利", "信念", "信心", "革命", "前途", "历史进程", "共同目标", "进步"),
        ),
    ),
    "毛泽东诗词": (
        (
            "archive-mao-poetry-mountains",
            ("山河", "攀登", "气魄", "青年担当", "进取"),
        ),
        (
            "archive-mao-poetry-river",
            ("胜利", "历史画面", "把握时机", "长远眼光", "主动"),
        ),
        (
            "archive-mao-poetry-snow",
            ("从容", "定力", "坚韧", "乐观"),
        ),
        (
            "archive-mao-poetry-long-march",
            ("长征", "奋斗", "重新出发", "行动", "目标"),
        ),
        (
            "archive-mao-poetry-plum-nature",
            ("奉献", "谦逊", "想象力", "时间"),
        ),
        (
            "archive-mao-poetry-city-change",
            ("建设", "时代变化", "时代担当", "历史进程", "历史评价"),
        ),
    ),
    "马原思考": (
        ("archive-marxism-industry", ("劳动",)),
        ("archive-marxism-labor", ("人的发展", "人民立场")),
        ("archive-marxism-books", ("理论与实践", "实践")),
        ("archive-marxism-city-society", ("社会关系",)),
        ("archive-marxism-international-movement", ("历史唯物主义",)),
        ("archive-marxism-speech-organization", ("马原思考", "辩证思维")),
    ),
    "名人名言": (
        ("archive-quotes-reading-writing", ("学习",)),
        ("archive-quotes-science", ("行动",)),
        ("archive-quotes-public-speech", ("关系",)),
        ("archive-quotes-civil-rights", ("自由",)),
        ("archive-quotes-journey", ("勇气",)),
        ("archive-quotes-nature", ("理想",)),
    ),
}

REVISION_PATTERN = re.compile(r"(?m)^revision: (?P<revision>\d+)(?=\r?$)")
IMAGE_PATTERN = re.compile(r"(?m)^imageId: (?P<image>[^\r\n]+)(?=\r?$)")


def select_image(card: dict) -> tuple[str, str]:
    series = card["literature"]["series"]
    rules = SERIES_RULES.get(series)
    if rules is None:
        raise ValueError(f"Unsupported series: {series}")
    themes = tuple(str(theme).strip() for theme in card.get("themes", []))
    scores = [
        sum(1 for theme in themes for keyword in keywords if keyword in theme)
        for _, keywords in rules
    ]
    best_score = max(scores, default=0)
    if best_score > 0:
        return rules[scores.index(best_score)][0], "关键词"

    stable_key = "|".join((series, *sorted(themes), card["id"]))
    slot = int(hashlib.sha256(stable_key.encode()).hexdigest()[:8], 16) % len(rules)
    return rules[slot][0], "稳定回退"


def replace_card_lines(path: Path, image_id: str) -> tuple[bool, str]:
    raw = path.read_bytes()
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    text = raw.decode("utf-8-sig")
    revision_match = REVISION_PATTERN.search(text)
    image_match = IMAGE_PATTERN.search(text)
    if revision_match is None or image_match is None:
        raise ValueError(f"Missing revision/imageId line: {path}")
    previous_image = image_match.group("image").strip(" '\"")
    if previous_image == image_id:
        return False, previous_image

    revision = int(revision_match.group("revision"))
    updated = REVISION_PATTERN.sub(f"revision: {revision + 1}", text, count=1)
    updated = IMAGE_PATTERN.sub(f"imageId: {image_id}", updated, count=1)

    before_lines = text.splitlines()
    after_lines = updated.splitlines()
    if len(before_lines) != len(after_lines):
        raise AssertionError(f"Line count changed for {path}")
    changed_lines = [
        (before, after)
        for before, after in zip(before_lines, after_lines, strict=True)
        if before != after
    ]
    if len(changed_lines) != 2 or any(
        not (before.startswith("revision:") or before.startswith("imageId:"))
        for before, _ in changed_lines
    ):
        raise AssertionError(f"Unexpected YAML changes for {path}: {changed_lines}")

    encoded = updated.encode("utf-8")
    if has_bom:
        encoded = b"\xef\xbb\xbf" + encoded
    temporary = path.with_suffix(f"{path.suffix}.tmp")
    temporary.write_bytes(encoded)
    temporary.replace(path)
    return True, previous_image


def write_report(
    counts: Counter[str],
    by_series: dict[str, Counter[str]],
    strategy_counts: Counter[str],
    changed_count: int,
) -> None:
    lines = [
        "# 档案照片映射报告",
        "",
        "- 映射输入：`literature.series + themes`；主题无明确关键词时使用系列、主题和卡片 ID 的稳定哈希回退。",
        "- 写入范围：仅修改根级 `revision:` 与 `imageId:` 两行；脚本逐文件断言行数不变且不存在第三处差异。",
        f"- 已发布卡片：`{sum(counts.values())}`",
        f"- 本次提升 revision：`{changed_count}`",
        f"- 关键词命中：`{strategy_counts['关键词']}`；稳定回退：`{strategy_counts['稳定回退']}`",
        "",
        "## 图片引用",
        "",
        "| 图片 ID | 引用数 |",
        "| --- | ---: |",
    ]
    for image_id, count in sorted(counts.items()):
        lines.append(f"| `{image_id}` | {count} |")
    lines.extend(("", "## 系列分布", ""))
    for series, series_counts in sorted(by_series.items()):
        lines.append(f"### {series}")
        lines.append("")
        for image_id, count in sorted(series_counts.items()):
            lines.append(f"- `{image_id}`：{count}")
        lines.append("")
    (ROOT / "image-mapping-report.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    planned: list[tuple[Path, str, str, str]] = []
    counts: Counter[str] = Counter()
    by_series: dict[str, Counter[str]] = defaultdict(Counter)
    strategy_counts: Counter[str] = Counter()
    published_count = 0
    for path in sorted(CARD_DIR.glob("*.yaml")):
        card = yaml.safe_load(path.read_text(encoding="utf-8"))
        if card.get("status") != "published":
            continue
        published_count += 1
        image_id, strategy = select_image(card)
        series = card["literature"]["series"]
        planned.append((path, image_id, strategy, series))
        counts[image_id] += 1
        by_series[series][image_id] += 1
        strategy_counts[strategy] += 1

    expected_ids = {
        image_id
        for rules in SERIES_RULES.values()
        for image_id, _ in rules
    }
    if published_count != 600:
        raise AssertionError(f"Expected 600 published cards, found {published_count}")
    if set(counts) != expected_ids:
        raise AssertionError(f"Unused image IDs: {sorted(expected_ids - set(counts))}")

    changed_count = 0
    for path, image_id, _, _ in planned:
        changed, _ = replace_card_lines(path, image_id)
        changed_count += int(changed)
    write_report(counts, by_series, strategy_counts, changed_count)
    print(f"Mapped {published_count} cards across {len(counts)} images; changed {changed_count} files")


if __name__ == "__main__":
    main()
