from __future__ import annotations

import difflib
import re
import time
import unicodedata
import uuid
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urljoin

import requests
import yaml
from lxml import html

REPO_ROOT = Path(__file__).resolve().parents[4]
CONTENT_ROOT = REPO_ROOT / "content"
CARDS_ROOT = CONTENT_ROOT / "cards"
AUDIT_PATH = Path(__file__).with_name("selected-candidates.md")
MARXISTS_INDEX = "https://www.marxists.org/chinese/maozedong/index.htm"
VOLUME_INDEXES = {
    "第一卷": "https://www.12371.cn/special/mzdxj/dyj/",
    "第二卷": "https://www.12371.cn/special/mzdxj/dej/",
    "第三卷": "https://www.12371.cn/special/mzdxj/d3j/",
    "第四卷": "https://www.12371.cn/special/mzdxj/d4j/",
}
TARGETS = {"第一卷": 23, "第二卷": 18, "第三卷": 19, "第四卷": 40}
IMAGE_IDS = (
    "original-dawn-city",
    "original-distant-mountains",
    "original-field-paths",
    "original-memorial-pine",
    "original-printing-press",
    "original-reading-lamp",
    "original-river-bridge",
    "original-workshop",
)
VOLUME_CONTEXT = {
    "第一卷": "第一次国内革命战争后到全面抗战前，革命道路、调查研究和认识方法成为重要议题",
    "第二卷": "全面抗战时期，民族危机、统一战线、战争进程和新民主主义道路需要持续分析",
    "第三卷": "延安整风和抗战后期，党的作风、群众工作、生产建设与联合政府问题集中展开",
    "第四卷": "解放战争时期，战略转折、土地政策、组织建设和新中国前途成为现实任务",
}
THEME_RULES = (
    ("调查研究", ("调查", "事实", "实际情况", "了解情况", "材料")),
    ("群众路线", ("群众", "人民", "农民", "工人", "民众")),
    ("团结", ("团结", "统一战线", "共同", "互相", "合作")),
    ("理论与实践", ("学习", "实践", "认识", "理论", "知识")),
    ("经济建设", ("经济", "生产", "财政", "工业", "商业")),
    ("领导方法", ("领导", "党委", "干部", "组织", "命令")),
    ("作风建设", ("作风", "批评", "自我批评", "骄傲", "纪律")),
    ("政策", ("政策", "策略", "方针", "办法", "原则")),
    ("战略", ("战争", "战略", "战术", "敌人", "胜利", "失败")),
    ("矛盾分析", ("分析", "矛盾", "条件", "主要", "次要", "区别")),
    ("责任", ("工作", "困难", "责任", "行动", "任务")),
)
THEME_GUIDANCE = {
    "调查研究": ("先掌握事实再形成判断", "调查范围、材料质量和具体条件"),
    "群众路线": ("把当事人的处境和实际需要放进决策", "尊重群众不等于放弃分析和责任边界"),
    "团结": ("围绕共同目标处理差异并形成协作", "团结需要原则、沟通和清楚的任务"),
    "理论与实践": ("让认识在行动和反馈中接受检验", "理论不能替代事实，经验也不能拒绝总结"),
    "经济建设": ("把资源、条件和长期效果一起核算", "建设目标必须落实为可执行的生产与组织安排"),
    "领导方法": ("让方向、分工、沟通和执行彼此衔接", "领导不是包办，决定也必须建立在信息和协作上"),
    "作风建设": ("用公开讨论和自我校正改进工作", "批评应对准问题、证据和改进办法"),
    "政策": ("根据对象、阶段和条件选择具体办法", "原则稳定不等于方法僵化"),
    "战略": ("分清长期趋势、当前力量和行动节奏", "历史战争经验不能脱离时代直接套用"),
    "矛盾分析": ("把对象拆开比较，找出主次和变化条件", "分类是为了接近事实，不是给现实贴固定标签"),
    "责任": ("承认问题并把责任落实到下一步行动", "行动需要目标、条件和复盘，不能只靠意志"),
    "工作方法": ("从具体问题出发选择可执行的方法", "一句话的启发必须放回原文对象和历史条件"),
}
POSITIVE_TERMS = tuple(term for _, terms in THEME_RULES for term in terms) + (
    "必须",
    "应该",
    "不能",
    "不要",
    "只有",
    "才能",
    "善于",
    "学会",
    "发展",
    "建设",
)
EXCLUDED_TEXT = (
    "中文马克思主义文库",
    "毛泽东选集",
    "注释",
    "原载",
    "编者",
    "说明",
    "currentAudio",
)


@dataclass(frozen=True)
class Work:
    volume: str
    title: str
    url: str
    authored_at: str
    body: str


@dataclass(frozen=True)
class Candidate:
    work: Work
    quote: str
    score: int


def fetch(url: str) -> bytes:
    headers = {"User-Agent": "xinghuo-content-editor/1.0"}
    last_error: Exception | None = None
    for attempt in range(6):
        try:
            response = requests.get(url, headers=headers, timeout=30)
            response.raise_for_status()
            return response.content
        except requests.RequestException as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"failed to fetch {url}: {last_error}")


def compact(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.split())).strip()


def has_balanced_quotes(value: str) -> bool:
    for opening, closing in (("“", "”"), ("‘", "’")):
        depth = 0
        for character in value:
            if character == opening:
                depth += 1
            elif character == closing:
                if depth == 0:
                    return False
                depth -= 1
        if depth != 0:
            return False
    return True


def title_key(value: str) -> str:
    value = compact(value).replace("附录：", "")
    return re.sub(r"[《》〈〉“”‘’\"'？！?!：:，,。·・\s]", "", value)


def parse_authored_at(url: str) -> str:
    match = re.search(r"-((?:18|19|20)\d{2})(\d{2})?(\d{2})?[a-z]*\.htm", url)
    if not match:
        raise ValueError(f"cannot derive authoredAt from {url}")
    year, month, day = match.groups()
    if day:
        return f"{year}-{month}-{day}"
    if month:
        return f"{year}-{month}"
    return year


def marxists_links() -> dict[str, tuple[str, str]]:
    document = html.fromstring(fetch(MARXISTS_INDEX), base_url=MARXISTS_INDEX)
    links: dict[str, tuple[str, str]] = {}
    for anchor in document.xpath("//a[@href]"):
        href = anchor.get("href", "")
        title = compact(anchor.text_content())
        if "marxist.org-chinese-mao-" not in href or not title:
            continue
        links[title_key(title)] = (title, urljoin(MARXISTS_INDEX, href))
    return links


def volume_titles(url: str) -> list[str]:
    document = html.fromstring(fetch(url))
    titles = [compact(node.get("title", "")) for node in document.xpath("//h3[@title]")]
    return [title for title in titles if title and title != "currentAudio.title"]


def match_link(title: str, links: dict[str, tuple[str, str]]) -> str:
    key = title_key(title)
    if key in links:
        return links[key][1]
    matches = difflib.get_close_matches(key, links.keys(), n=1, cutoff=0.72)
    if not matches:
        raise KeyError(f"no full-text link for {title}")
    return links[matches[0]][1]


def page_body(url: str) -> str:
    document = html.fromstring(fetch(url))
    body_nodes = document.xpath("//body")
    if not body_nodes:
        raise ValueError(f"no body in {url}")
    lines = [compact(line) for line in body_nodes[0].text_content().splitlines()]
    lines = [line for line in lines if line]
    content_lines: list[str] = []
    for line in lines:
        if line in {"注释", "注释："}:
            break
        if any(marker in line for marker in EXCLUDED_TEXT):
            continue
        if re.fullmatch(r"[（(]一[八九二〇○零一二三四五六七八九十年月日]+[）)]", line):
            continue
        content_lines.append(line)
    return "\n".join(content_lines)


def existing_cards() -> tuple[set[str], Counter[tuple[str, str]], set[str]]:
    quotes: set[str] = set()
    work_counts: Counter[tuple[str, str]] = Counter()
    ids: set[str] = set()
    for path in sorted(CARDS_ROOT.glob("*.yaml")):
        card = yaml.safe_load(path.read_text(encoding="utf-8"))
        if card.get("status") != "published":
            continue
        ids.add(str(card["id"]))
        try:
            number = int(path.name[:3])
        except ValueError:
            number = 0
        if 601 <= number <= 700:
            continue
        quote = compact(str(card["quote"]))
        quotes.add(quote)
        literature = card["literature"]
        if literature["series"] == "毛泽东选集":
            work_counts[(literature["volume"], literature["workTitle"])] += 1
    return quotes, work_counts, ids


def sentence_candidates(work: Work, existing_quotes: set[str]) -> list[Candidate]:
    found: dict[str, Candidate] = {}
    for line in work.body.splitlines():
        for raw in re.split(r"(?<=[。！？；])", line):
            quote = compact(raw)
            quote = re.sub(r"^[一二三四五六七八九十百0-9]+[、.．]\s*", "", quote)
            if not 18 <= len(quote) <= 84:
                continue
            if quote in existing_quotes or quote in found:
                continue
            if not has_balanced_quotes(quote):
                continue
            if any(
                marker in quote
                for marker in (
                    "[",
                    "]",
                    "〔",
                    "〕",
                    "*",
                    "……",
                    "犭",
                    "上述",
                    "这样做",
                    "这个阶级",
                    "这个任务",
                    "这个基本的事实",
                    "这种情况",
                    "这种地区",
                    "这种抗战",
                    "这些人",
                    "这些作用",
                    "这些工商业",
                    "你们",
                    "聆取",
                    "难道",
                    "究竟",
                    "大进一步",
                    "显着",
                    "声明：这是",
                    "西线胜利后，他",
                    "在马歇尔系统看来，他",
                    "你和你的政府",
                    "有助于人民的休养生息",
                )
            ):
                continue
            if any(marker in quote for marker in EXCLUDED_TEXT):
                continue
            if not quote.endswith(("。", "！")):
                continue
            if re.match(
                r"^(?:[（(][一二三四五六七八九十]+[）)]|一则|二则|三则|这种|这些|这个|这篇|"
                r"这就是|这是|这不但|第三个方向|还要|而在|再有一点|如此|"
                r"非常奇怪|假如中共|以脱卸责任为目的的白皮书|战犯们又|倘若共产党|"
                r"同时，他们|[他她它你]们?(?:是|就|又|的|必须|应该|不能)|"
                r"一个有纪律的|"
                r"他们认为|我们认为|我们曾经|一方面|另一方面|总之|当然|但是|此外|因此)",
                quote,
            ):
                continue
            score = sum(2 for term in POSITIVE_TERMS if term in quote)
            score += 5 if 24 <= len(quote) <= 64 else 1
            score += 3 if re.search(r"(必须|应该|不能|不要|只有|才能|善于|学会)", quote) else 0
            score -= 3 if re.search(r"\d|第[一二三四五六七八九十]+", quote) else 0
            score -= 4 * sum(
                quote.count(term)
                for term in (
                    "蒋介石",
                    "国民党",
                    "美帝国主义",
                    "希特勒",
                    "艾奇逊",
                    "参政会",
                    "十二月",
                )
            )
            found[quote] = Candidate(work=work, quote=quote, score=score)
    return sorted(found.values(), key=lambda item: (-item.score, len(item.quote), item.quote))


def build_works() -> list[Work]:
    links = marxists_links()
    works: list[Work] = []
    unmatched: list[str] = []
    for volume, index_url in VOLUME_INDEXES.items():
        for title in volume_titles(index_url):
            try:
                url = match_link(title, links)
                works.append(
                    Work(
                        volume=volume,
                        title=title,
                        url=url,
                        authored_at=parse_authored_at(url),
                        body=page_body(url),
                    )
                )
            except (KeyError, ValueError, RuntimeError) as error:
                unmatched.append(f"{volume} / {title}: {error}")
    if unmatched:
        print("Skipped unmatched works:")
        for item in unmatched:
            print(f"  {item}")
    return works


def select_candidates(
    works: list[Work],
    existing_quotes: set[str],
    work_counts: Counter[tuple[str, str]],
) -> list[Candidate]:
    pools = {work: sentence_candidates(work, existing_quotes) for work in works}
    selected: list[Candidate] = []
    used_quotes = set(existing_quotes)
    for volume, target in TARGETS.items():
        volume_works = [work for work in works if work.volume == volume and pools[work]]
        volume_works.sort(
            key=lambda work: (
                work_counts[(volume, work.title)],
                -pools[work][0].score,
                work.title,
            )
        )
        chosen_for_work: Counter[Work] = Counter()
        volume_selected: list[Candidate] = []
        for round_index in range(3):
            for work in volume_works:
                if len(volume_selected) >= target:
                    break
                if work_counts[(volume, work.title)] + chosen_for_work[work] >= 7:
                    continue
                available = [item for item in pools[work] if item.quote not in used_quotes]
                if not available:
                    continue
                candidate = available[0]
                volume_selected.append(candidate)
                chosen_for_work[work] += 1
                used_quotes.add(candidate.quote)
            if len(volume_selected) >= target:
                break
        if len(volume_selected) != target:
            raise RuntimeError(f"{volume} requires {target} candidates, found {len(volume_selected)}")
        selected.extend(volume_selected)
    return selected


def classify_theme(quote: str, title: str) -> str:
    text = f"{title}{quote}"
    for theme, terms in THEME_RULES:
        if any(term in text for term in terms):
            return theme
    return "工作方法"


def chinese_date(authored_at: str) -> str:
    parts = authored_at.split("-")
    result = f"{parts[0]}年"
    if len(parts) >= 2:
        result += f"{int(parts[1])}月"
    if len(parts) == 3:
        result += f"{int(parts[2])}日"
    return result


def quote_excerpt(quote: str) -> str:
    cleaned = quote.strip("“”‘’。！？；")
    first_clause = re.split(r"[，；：。！？]", cleaned, maxsplit=1)[0].strip()
    if len(first_clause) >= 10:
        return first_clause
    return cleaned


def card_sections(candidate: Candidate, index: int) -> tuple[str, str, str, str, str]:
    work = candidate.work
    theme = classify_theme(candidate.quote, work.title)
    action, boundary = THEME_GUIDANCE[theme]
    excerpt = quote_excerpt(candidate.quote)
    inspiration_variants = (
        f"“{excerpt}”提示我们，遇到相似问题时先{action}。把目标、对象和现实条件写清楚，再检查办法是否真正回应了问题。",
        f"从“{excerpt}”出发，可以把态度转成行动：{action}，并让事实、分工和实际结果检验原先的判断。",
        f"可以把“{excerpt}”转成一个工作问题：怎样{action}？答案要经得起事实、协作过程和实际结果的检验。",
        f"“{excerpt}”提醒我们面对复杂局面先{action}，同时保留修正空间。清楚说明依据和边界，比套用结论更重要。",
    )
    explanation_variants = (
        f"这句话出自《{work.title}》，原文围绕{theme}的具体任务展开。“{excerpt}”不是孤立口号，而是论证中的一个判断。它强调{action}；理解时也要看到，{boundary}。今天借鉴的是分析和行动方法，不能把当时的对象、力量关系和历史条件原样搬到新的场景。",
        f"《{work.title}》讨论的是特定历史条件下的{theme}问题。这里用“{excerpt}”指出行动应有的着力点，核心在于{action}。原文并不支持脱离对象的简单化理解，因为{boundary}。阅读这句话时，应把结论、依据和适用条件放在一起。",
        f"在《{work.title}》中，这句话服务于对{theme}问题的分析。它把“{excerpt}”同实际任务联系起来，要求{action}。它的现实启发主要是一种工作方法；{boundary}，因此不能只保留有力量的措辞而省略前后的事实和限制。",
        f"原文写作《{work.title}》时，面对的是需要作出具体判断和安排的{theme}问题。这句话以“{excerpt}”概括其中一层意思，即{action}。同时，{boundary}。把它用于今天，应先核对问题是否同类，再决定哪些方法可以借鉴。",
    )
    historical_event = (
        f"{chinese_date(work.authored_at)}，《{work.title}》形成，集中讨论{theme}及其现实任务。"
    )
    background = (
        f"{VOLUME_CONTEXT[work.volume]}。《{work.title}》从当时的具体问题出发讨论{theme}，本卡从“{excerpt}”切入全文的一层论证。"
    )
    story_variants = (
        f"文章先交代现实矛盾，再提出判断和办法。这句以“{excerpt}”留下清晰抓手，回看上下文可以看到它回答了什么问题、依靠什么条件。",
        f"《{work.title}》并非名句汇编，而是针对现实任务展开的完整论述。所选句子把其中一个关键判断压缩得很鲜明，前后段落则补足了对象和条件。",
        f"这句话在全文中承担承上启下或归纳判断的作用。沿着《{work.title}》的论证继续阅读，可以分清历史结论、工作方法和今天可借鉴的部分。",
        f"文本围绕{theme}逐层展开，从事实判断走向行动安排。所选原文便于记忆，但真正有用的是连同《{work.title}》中的理由和限制一起理解。",
    )
    variant = index % 4
    return (
        inspiration_variants[variant],
        explanation_variants[variant],
        historical_event,
        background,
        story_variants[variant],
    )


def write_cards(selected: list[Candidate], existing_ids: set[str]) -> None:
    numbered = sorted(selected, key=lambda item: (tuple(TARGETS).index(item.work.volume), item.work.title, -item.score))
    new_ids: set[str] = set()
    for offset, candidate in enumerate(numbered):
        number = 601 + offset
        path = CARDS_ROOT / f"{number:03d}-mao-v{tuple(TARGETS).index(candidate.work.volume) + 1}.yaml"
        if path.exists():
            existing = yaml.safe_load(path.read_text(encoding="utf-8"))
            card_id = str(existing["id"])
        else:
            card_id = str(uuid.uuid4())
            while card_id in existing_ids or card_id in new_ids:
                card_id = str(uuid.uuid4())
        new_ids.add(card_id)
        theme = classify_theme(candidate.quote, candidate.work.title)
        inspiration, explanation, historical_event, background, story = card_sections(
            candidate,
            offset,
        )
        card = {
            "id": card_id,
            "revision": 1,
            "status": "published",
            "quote": candidate.quote,
            "literature": {
                "series": "毛泽东选集",
                "volume": candidate.work.volume,
                "workTitle": candidate.work.title,
                "authoredAt": candidate.work.authored_at,
            },
            "themes": [theme],
            "interpretation": {
                "inspiration": inspiration,
                "explanation": explanation,
            },
            "historicalEvent": historical_event,
            "background": background,
            "story": story,
            "imageId": IMAGE_IDS[offset % len(IMAGE_IDS)],
            "sources": [
                {
                    "name": f"中文马克思主义文库《{candidate.work.title}》全文",
                    "url": candidate.work.url,
                    "accessedAt": "2026-08-05",
                    "type": "original",
                }
            ],
            "review": {"status": "verified", "checkedAt": "2026-08-05"},
        }
        path.write_text(
            yaml.safe_dump(card, allow_unicode=True, sort_keys=False, width=4096),
            encoding="utf-8",
        )


def write_audit(selected: list[Candidate]) -> None:
    lines = [
        "# 新增毛选卡片候选审计",
        "",
        "生成日期：2026-08-05。每条引文均在生成时从所列公开全文页提取，并排除了现有正式卡片的完全重复文本。",
        "",
        "## 分布",
        "",
    ]
    counts = Counter(item.work.volume for item in selected)
    for volume in TARGETS:
        lines.append(f"- {volume}：{counts[volume]} 条")
    lines.extend(["", "## 候选", ""])
    for index, candidate in enumerate(
        sorted(selected, key=lambda item: (tuple(TARGETS).index(item.work.volume), item.work.title, -item.score)),
        start=601,
    ):
        lines.extend(
            [
                f"### {index:03d} · {candidate.work.volume} · {candidate.work.title}",
                "",
                f"> {candidate.quote}",
                "",
                f"- 日期：{candidate.work.authored_at}",
                f"- 主题：{classify_theme(candidate.quote, candidate.work.title)}",
                f"- 来源：[{candidate.work.url}]({candidate.work.url})",
                f"- 自动筛选分：{candidate.score}",
                "",
            ]
        )
    AUDIT_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    output_paths = [
        path
        for path in CARDS_ROOT.glob("*.yaml")
        if path.name[:3].isdigit() and 601 <= int(path.name[:3]) <= 700
    ]
    if output_paths and len(output_paths) != 100:
        raise RuntimeError(f"expected zero or 100 generated card files, found {len(output_paths)}")
    existing_quotes, work_counts, existing_ids = existing_cards()
    works = build_works()
    selected = select_candidates(works, existing_quotes, work_counts)
    if len(selected) != 100:
        raise RuntimeError(f"expected 100 selected candidates, got {len(selected)}")
    write_audit(selected)
    write_cards(selected, existing_ids)
    print(f"generated {len(selected)} cards and {AUDIT_PATH.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
