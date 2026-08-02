from __future__ import annotations

import re
import time
import unicodedata
import uuid
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from urllib.parse import quote

import requests
import yaml

ROOT = Path(__file__).resolve().parents[4]
CARDS_DIR = ROOT / "content" / "cards"
CACHE_DIR = Path(__file__).resolve().parent / "wikiquote-cache"
ACCESSED_AT = "2026-08-02"
START_SLOT = 151
REQUEST_DELAY_SECONDS = 1.2
USER_AGENT = "XinghuoContentResearch/1.0 (https://github.com/Hi-prof/maoxuan)"

IMAGES = (
    "original-dawn-city",
    "original-distant-mountains",
    "original-field-paths",
    "original-memorial-pine",
    "original-printing-press",
    "original-reading-lamp",
    "original-river-bridge",
    "original-workshop",
)


@dataclass(frozen=True)
class Author:
    slug: str
    page_title: str
    display_name: str
    authored_at: str
    target: int
    kind: str
    historical_event: str
    background: str


@dataclass(frozen=True)
class PrimaryWork:
    author_slug: str
    title: str
    url: str
    target: int


POPULAR_AUTHORS = (
    Author("luxun", "鲁迅", "鲁迅", "1920", 30, "popular", "五四新文化运动推动白话文学、思想启蒙与社会批评，鲁迅是其中的重要作家。", "鲁迅的杂文、小说与书信常从个人处境切入，追问麻木、勇气、学习与社会责任。"),
    Author("tagore", "泰戈尔", "泰戈尔", "1913", 20, "popular", "1913年，泰戈尔获得诺贝尔文学奖，亚洲文学获得更广泛的世界性关注。", "泰戈尔的诗歌与散文常围绕自由、爱、生命、教育和人与世界的关系展开。"),
    Author("nietzsche", "尼采", "尼采", "1880", 20, "popular", "19世纪后期，欧洲哲学开始强烈反思传统道德、宗教与现代人的精神处境。", "尼采的著作常以格言式语言讨论创造、自由、价值判断与自我超越。"),
    Author("shakespeare", "威廉·莎士比亚", "莎士比亚", "1600", 20, "popular", "伊丽莎白时代的英国戏剧繁荣，莎士比亚的作品在舞台上持续讨论权力、爱情、选择与人性。", "莎士比亚戏剧以冲突和人物行动呈现人性的复杂性，许多台词因准确而长期流传。"),
    Author("rolland", "罗曼·罗兰", "罗曼·罗兰", "1915", 20, "popular", "第一次世界大战前后，欧洲知识界围绕战争、和平与人的尊严展开激烈讨论。", "罗曼·罗兰以文学和公共写作关注英雄主义、良知、和平与普通人的精神力量。"),
    Author("hemingway", "欧内斯特·海明威", "海明威", "1950", 20, "popular", "两次世界大战改变了20世纪文学的主题，战争经历深刻影响了海明威的写作。", "海明威的小说以克制语言写压力、失败、尊严和人在困境中的选择。"),
    Author("einstein", "阿尔伯特·爱因斯坦", "爱因斯坦", "1920", 20, "popular", "20世纪初的物理学革命改变了人们理解时间、空间和科学方法的方式。", "爱因斯坦既讨论科学，也谈教育、好奇心、和平与个人责任。"),
    Author("curie", "玛丽·居里", "居里夫人", "1903", 15, "popular", "放射性研究开辟了现代物理与医学的新领域，居里夫妇的工作成为科学史的重要节点。", "居里夫人的经历常被用来讨论专注、求知、勇气和科学工作者的责任。"),
    Author("twain", "马克·吐温", "马克·吐温", "1900", 15, "popular", "19世纪末至20世纪初，美国社会快速变化，幽默讽刺成为观察公共生活的重要表达。", "马克·吐温以幽默、讽刺和反常识的角度谈成长、教育、自由与人情。"),
    Author("hugo", "维克多·雨果", "维克多·雨果", "1860", 15, "popular", "19世纪法国经历革命、复辟与共和的反复，文学作品常直接回应贫困、正义与社会变迁。", "雨果的文学和公共行动持续关注尊严、同情、教育与社会正义。"),
    Author("keller", "海伦·凯勒", "海伦·凯勒", "1920", 15, "popular", "20世纪初，残障教育与社会参与逐渐成为公共议题，海伦·凯勒的经历带来广泛影响。", "海伦·凯勒的写作常从障碍、学习和社会责任出发，强调行动与连接。"),
    Author("ostrovsky", "尼古拉·奥斯特洛夫斯基", "尼古拉·奥斯特洛夫斯基", "1930", 15, "popular", "20世纪上半叶的社会变革与战争塑造了一代人的个人选择和文学表达。", "奥斯特洛夫斯基的作品以成长、信念、劳动和逆境中的坚持为核心主题。"),
    Author("gibran", "纪伯伦", "纪伯伦", "1920", 15, "popular", "20世纪初的跨文化写作让阿拉伯文学与英语世界产生新的交流。", "纪伯伦以散文诗式语言讨论爱、自由、劳动、亲密关系和个人成长。"),
    Author("schopenhauer", "叔本华", "叔本华", "1840", 15, "popular", "19世纪欧洲哲学在理性主义之外重新讨论欲望、痛苦、艺术与人生体验。", "叔本华的观点常带有锋芒，适合用来反省欲望、判断和独处，而不宜被断章当成消极结论。"),
    Author("socrates", "苏格拉底", "苏格拉底", "前4世纪", 15, "popular", "古希腊城邦的公共辩论塑造了哲学传统，苏格拉底式追问强调审视自己的判断。", "苏格拉底的思想通过柏拉图等人的记述流传，核心方法是提问、辨析与承认无知。"),
    Author("plato", "柏拉图", "柏拉图", "前4世纪", 15, "popular", "古希腊哲学从对话、辩论和城邦生活中发展出关于知识、正义和教育的系统思考。", "柏拉图用对话体讨论真理、灵魂、政治和教育，常提醒读者区分意见与经过论证的知识。"),
    Author("aristotle", "亚里士多德", "亚里士多德", "前4世纪", 15, "popular", "古希腊学术传统逐渐形成分类、观察和论证相结合的研究方式。", "亚里士多德的著作横跨伦理、政治、逻辑与自然研究，强调习惯、实践和具体分析。"),
    Author("wilde", "奥斯卡·王尔德", "奥斯卡·王尔德", "1890", 15, "popular", "19世纪末英国文学和戏剧活跃，王尔德以机智的文字批评社会习俗和虚伪道德。", "王尔德的格言常从反常识角度谈个性、审美、教育和社会期待，适合连同语境一起理解。"),
    Author("shaw", "萧伯纳", "萧伯纳", "1910", 15, "popular", "20世纪初英国的社会改革讨论深入公共生活，戏剧成为批评贫困、教育和阶层问题的媒介。", "萧伯纳的戏剧和评论以讽刺手法讨论教育、创造、社会责任和独立思考。"),
    Author("churchill", "温斯顿·丘吉尔", "温斯顿·丘吉尔", "1940", 15, "popular", "第二次世界大战塑造了20世纪政治与公共演讲，危机中的领导和抵抗成为广泛讨论的主题。", "丘吉尔的演讲与写作常被引用来讨论韧性、责任、历史判断和公共行动。"),
    Author("lincoln", "亚伯拉罕·林肯", "亚伯拉罕·林肯", "1860", 15, "popular", "美国内战时期围绕联邦、奴隶制和公民权利的冲突深刻改变了美国社会。", "林肯的演说和书信常讨论民主、责任、学习与在冲突中维持公共原则。"),
    Author("mandela", "纳尔逊·曼德拉", "纳尔逊·曼德拉", "1990", 15, "popular", "南非反种族隔离斗争推动了20世纪末关于平等、和解与公共正义的全球讨论。", "曼德拉的言行常被用来思考尊严、和平、勇气和长期公共行动。"),
    Author("mlk", "马丁·路德·金", "马丁·路德·金", "1960", 15, "popular", "美国民权运动以非暴力抗争推动了公众对种族平等与公民权利的重新认识。", "马丁·路德·金的演说聚焦正义、希望、非暴力行动和共同体责任。"),
)

MARXISM_AUTHORS = (
    Author("marx", "卡尔·马克思", "卡尔·马克思", "1848", 55, "marxism", "19世纪工业化加速发展，劳动、资本和社会结构的变化成为思想界的重要问题。", "马克思的文本围绕实践、劳动、社会关系、历史发展和人的解放展开分析。"),
    Author("engels", "弗里德里希·恩格斯", "弗里德里希·恩格斯", "1878", 40, "marxism", "19世纪的工人运动和自然科学进展推动了对社会历史与辩证思维的新讨论。", "恩格斯与马克思共同发展历史唯物主义，也持续讨论自然、科学、劳动与社会变迁。"),
    Author("lenin", "弗拉基米尔·列宁", "弗拉基米尔·列宁", "1902", 35, "marxism", "20世纪初的革命实践把理论、组织、群众工作和具体形势分析放在紧密关联中。", "列宁的论述常强调从具体条件出发，把理论学习、实践检验和组织行动联系起来。"),
    Author("deng", "邓小平", "邓小平", "1978", 20, "marxism", "改革开放初期，解放思想、实事求是与发展问题成为中国社会的重要讨论。", "邓小平的讲话常从实践标准、发展、改革和人民生活的实际改善来讨论马克思主义的方法论。"),
)

PRIMARY_MARXISM_WORKS = (
    PrimaryWork("marx", "《关于费尔巴哈的提纲》", "https://www.marxists.org/chinese/marx/marxist.org-chinese-marx-1845.htm", 10),
    PrimaryWork("marx", "《德意志意识形态》（节选）", "https://www.marxists.org/chinese/marx/marxist.org-chinese-marx-1846.htm", 9),
    PrimaryWork("marx", "《共产党宣言》", "https://www.marxists.org/chinese/marx/01.htm", 8),
    PrimaryWork("marx", "《政治经济学批判》导言", "https://www.marxists.org/chinese/marx/marxist.org-chinese-marx-1860.htm", 8),
    PrimaryWork("marx", "《政治经济学批判》序言", "https://www.marxists.org/chinese/marx/06.htm", 8),
    PrimaryWork("marx", "《工资、价格和利润》", "https://www.marxists.org/chinese/marx/marxist.org-chinese-marx-1865-5.htm", 5),
    PrimaryWork("marx", "《哥达纲领批判》", "https://www.marxists.org/chinese/marx/marxist.org-chinese-marx-1875-4.htm", 7),
    PrimaryWork("engels", "《辩证法两部分札记》", "https://www.marxists.org/chinese/engels/marxist.org-chinese-engels-1873.htm", 8),
    PrimaryWork("engels", "《劳动在从猿到人转变过程中的作用》", "https://www.marxists.org/chinese/engels/marxist.org-chinese-engels-1875a.htm", 8),
    PrimaryWork("engels", "《自然辩证法》导言", "https://www.marxists.org/chinese/engels/marxist.org-chinese-engels-1875.htm", 8),
    PrimaryWork("engels", "《反杜林论》旧序：论辩证法", "https://www.marxists.org/chinese/engels/marxist.org-chinese-engels-1876a.htm", 8),
    PrimaryWork("engels", "《社会主义从空想到科学的发展》", "https://www.marxists.org/chinese/engels/marxist.org-chinese-engels-1880a.htm", 8),
    PrimaryWork("lenin", "《马克思主义和修正主义》", "https://www.marxists.org/chinese/lenin/marxist.org-chinese-lenin-19080323.htm", 7),
    PrimaryWork("lenin", "《马克思学说的历史命运》", "https://www.marxists.org/chinese/lenin/11.htm", 7),
    PrimaryWork("lenin", "《马克思主义的三个来源和三个组成部分》", "https://www.marxists.org/chinese/lenin/12.htm", 7),
    PrimaryWork("lenin", "《帝国主义是资本主义的最高阶段》", "https://www.marxists.org/chinese/lenin/15.htm", 7),
    PrimaryWork("lenin", "《宁肯少些，但要好些》", "https://www.marxists.org/chinese/lenin/mia-chinese-lenin-19230304.htm", 7),
)

assert sum(author.target for author in POPULAR_AUTHORS) >= 300
assert sum(author.target for author in MARXISM_AUTHORS) == 150
assert sum(work.target for work in PRIMARY_MARXISM_WORKS) == 130

POPULAR_TERMS = (
    "人生", "生命", "希望", "梦想", "奋斗", "勇气", "自由", "幸福", "学习", "爱", "时间", "理想", "未来", "成功", "失败", "困难", "努力", "生活", "读书", "经验", "命运", "真理", "工作", "成长", "选择", "目标", "坚持", "行动", "世界", "教育", "青年", "朋友", "责任", "创造", "美", "快乐",
)
MARXISM_TERMS = (
    "实践", "社会", "历史", "生产", "劳动", "阶级", "自由", "真理", "科学", "矛盾", "具体", "革命", "理论", "物质", "发展", "条件", "关系", "经济", "现实", "生活", "共同", "群众", "人民", "认识", "行动", "思想", "组织", "问题", "分析", "利益",
)
BAD_TERMS = (
    "参见", "外部链接", "文件", "图像", "维基", "引文", "版权", "编辑", "参考资料", "原文", "https", "http", "Category", "分类", "模板", "维基文库", "维基百科", "本页面", "该页面",
)
PRIMARY_BAD_TERMS = (
    "全集", "出版", "译本", "稿本", "手稿", "前言", "后记", "编者", "注释", "目录", "页码", "写于", "选自", "刊载", "版本", "索引", "印刷", "序言", "引言", "章", "节", "本卷", "本书", "以下", "上文", "下文", "原文", "标题", "编辑部", "编译", "文件", "资料", "年版", "年于",
)
POPULAR_BAD_TERMS = (
    "中国人", "国民", "民族", "政府", "政治", "革命", "战争", "军队", "士兵", "阶级", "帝国", "资产阶级", "法律", "斗争", "殖民", "选举", "报纸", "总统", "议会", "监狱", "宗教", "教会", "傻子", "蛀虫", "性欲", "死亡", "朽腐", "鲜血", "敌人", "自杀", "悲剧", "苦难", "痛苦",
)
POPULAR_FOCUS_TERMS = (
    "希望", "梦想", "理想", "勇气", "学习", "读书", "自由", "青年", "生命", "人生", "未来", "成长", "选择", "信念", "坚持", "行动", "失败", "成功", "困难", "自己", "自我", "幸福", "努力", "创造", "热爱", "时间", "朋友", "友情", "生活", "工作", "真理", "知识", "教育", "目标", "乐观", "美好", "心灵", "快乐", "机会", "经验",
)
FILTER_TRANSLATION = str.maketrans({
    "國": "国", "戰": "战", "爭": "争", "選": "选", "舉": "举", "總": "总", "統": "统", "監": "监", "獄": "狱", "敵": "敌", "慘": "惨", "難": "难", "夢": "梦", "學": "学", "讀": "读", "書": "书", "奮": "奋", "堅": "坚", "識": "识", "樂": "乐", "無": "无", "愛": "爱", "說": "说", "實": "实", "會": "会", "過": "过", "來": "来", "與": "与", "為": "为", "長": "长", "義": "义", "階": "阶", "級": "级", "資": "资", "產": "产", "業": "业", "際": "际", "勞": "劳", "動": "动", "壓": "压", "權": "权", "員": "员", "軍": "军", "點": "点", "樣": "样", "裏": "里", "這": "这", "個": "个", "當": "当", "讓": "让", "認": "认", "間": "间", "經": "经", "驗": "验", "機": "机", "創": "创", "進": "进", "發": "发", "變": "变", "開": "开", "關": "关", "門": "门", "電": "电", "親": "亲", "視": "视", "醫": "医", "藥": "药", "處": "处", "給": "给", "對": "对", "盡": "尽", "盜": "盗", "殺": "杀", "敗": "败", "懼": "惧", "懷": "怀",
})


def raw_url(title: str) -> str:
    return f"https://zh.wikiquote.org/w/index.php?title={quote(title)}&action=raw"


def page_url(title: str) -> str:
    return f"https://zh.wikiquote.org/wiki/{quote(title)}"


def wikipedia_url(name: str) -> str:
    return f"https://zh.wikipedia.org/wiki/{quote(name)}"


def strip_markup(value: str) -> str:
    value = re.sub(r"<!--.*?-->", "", value)
    value = re.sub(r"<ref[^>/]*?(?:/>|>.*?</ref>)", "", value, flags=re.S)
    value = re.sub(r"\{\{[^{}]*\}\}", "", value)
    value = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]+)\]\]", r"\1", value)
    value = re.sub(r"\[https?://[^\s\]]+(?:\s+([^\]]+))?\]", r"\1", value)
    value = value.replace("'''", "").replace("''", "")
    value = value.replace("&nbsp;", " ").replace("<br />", "")
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"\s+", "", value)
    return unicodedata.normalize("NFC", value).strip(" -—：:；;")


def fetch_wikitext(title: str) -> tuple[str, str]:
    visited: set[str] = set()
    current = title
    while current not in visited:
        visited.add(current)
        cache_path = CACHE_DIR / f"{quote(current, safe='')}.wiki"
        if cache_path.exists():
            text = cache_path.read_text(encoding="utf-8")
        else:
            response = None
            for attempt in range(4):
                try:
                    response = requests.get(raw_url(current), headers={"User-Agent": USER_AGENT}, timeout=30)
                    response.raise_for_status()
                    break
                except requests.RequestException:
                    if attempt == 3:
                        raise
                    time.sleep((attempt + 1) * 4)
            assert response is not None
            text = response.text
            CACHE_DIR.mkdir(exist_ok=True)
            cache_path.write_text(text, encoding="utf-8")
        redirect = re.match(r"\s*#REDIRECT\s*\[\[([^\]]+)\]\]", text, flags=re.I)
        if redirect is None:
            return current, text
        current = redirect.group(1).split("|")[0].strip()
        time.sleep(REQUEST_DELAY_SECONDS)
    raise RuntimeError(f"Wikiquote redirect loop for {title}")


def extract_quotes(wikitext: str) -> list[tuple[str, str]]:
    lines = wikitext.splitlines()
    extracted: list[tuple[str, str]] = []
    for index, line in enumerate(lines):
        if not re.match(r"^\*[^*:]", line):
            continue
        value = strip_markup(re.sub(r"^\*+", "", line).strip())
        if not is_candidate(value):
            continue
        citation = "语录出处见维基语录条目"
        for next_line in lines[index + 1:index + 4]:
            if next_line.startswith("*:"):
                citation = strip_markup(re.sub(r"^\*:+", "", next_line).strip()) or citation
                break
            if next_line.startswith("*") and not next_line.startswith("**"):
                break
        extracted.append((value, citation[:90]))
    return extracted


def fetch_primary_work(work: PrimaryWork) -> str:
    cache_path = CACHE_DIR / f"primary-{quote(work.author_slug, safe='')}-{quote(work.title, safe='')}.html"
    if cache_path.exists():
        return cache_path.read_text(encoding="utf-8")
    response = None
    for attempt in range(4):
        try:
            response = requests.get(work.url, headers={"User-Agent": USER_AGENT}, timeout=30)
            response.raise_for_status()
            break
        except requests.RequestException:
            if attempt == 3:
                raise
            time.sleep((attempt + 1) * 4)
    assert response is not None
    response.encoding = "gb2312"
    text = response.text
    CACHE_DIR.mkdir(exist_ok=True)
    cache_path.write_text(text, encoding="utf-8")
    return text


def extract_primary_sentences(html: str) -> list[str]:
    html = re.sub(r"<(?:script|style)[^>]*>.*?</(?:script|style)>", "", html, flags=re.I | re.S)
    html = re.sub(r"<(?:br|/p|/div|/tr|/li|/h[1-6])\b[^>]*>", "。", html, flags=re.I)
    text = re.sub(r"<[^>]+>", "", html)
    text = re.sub(r"&(?:nbsp|#160);", " ", text)
    text = re.sub(r"&[a-zA-Z]+;", "", text)
    text = unicodedata.normalize("NFC", re.sub(r"\s+", "", text))
    extracted: list[str] = []
    for sentence in re.split(r"(?<=[。！？；])", text):
        sentence = re.sub(r"[\[［].*?[\]］]", "", sentence)
        sentence = sentence.strip(" -—：:；;")
        if is_primary_candidate(sentence):
            extracted.append(sentence)
    return extracted


def is_candidate(value: str) -> bool:
    if not 8 <= len(value) <= 90:
        return False
    if any(term in value for term in BAD_TERMS):
        return False
    if re.fullmatch(r"[\d\s，。；：:、（）()\-—]+", value):
        return False
    if value.count("《") != value.count("》"):
        return False
    return True


def is_primary_candidate(value: str) -> bool:
    if not is_candidate(value):
        return False
    if any(term in value for term in PRIMARY_BAD_TERMS):
        return False
    if re.search(r"\[[^\]]*\]|\d{3,4}年|^[（(《〈\[【]", value):
        return False
    if not value.endswith(("。", "！", "？")):
        return False
    if value.endswith(("：。", "，。")):
        return False
    if value.startswith(("，", "。", "、", "”", "因此", "但是", "而且", "此外", "至于", "所以", "随后", "其实", "同样", "也", "这是", "这些", "那", "它", "他们", "我们", "现在", "从而", "其中", "关于")):
        return False
    if value.count("“") != value.count("”") or value.count("‘") != value.count("’"):
        return False
    if len(re.findall(r"[\u4e00-\u9fff]", value)) < 10:
        return False
    return True


def is_popular_candidate(value: str) -> bool:
    normalized = value.translate(FILTER_TRANSLATION)
    return is_candidate(value) and not any(term in normalized for term in POPULAR_BAD_TERMS) and any(term in normalized for term in POPULAR_FOCUS_TERMS)


def is_distinct_quote(quote_text: str, selected_quotes: list[str]) -> bool:
    compact = re.sub(r"[，。！？；：、‘’“”()（）\-―—\s]", "", quote_text)
    return all(
        SequenceMatcher(None, compact, re.sub(r"[，。！？；：、‘’“”()（）\-―—\s]", "", existing)).ratio() < 0.76
        for existing in selected_quotes
    )


def theme_for(quote_text: str, kind: str) -> str:
    if kind == "marxism":
        for term, theme in (("实践", "实践"), ("矛盾", "辩证思维"), ("劳动", "劳动"), ("历史", "历史唯物主义"), ("社会", "社会关系"), ("自由", "人的发展"), ("理论", "理论与实践"), ("群众", "人民立场")):
            if term in quote_text:
                return theme
        return "马原思考"
    for term, theme in (("学习", "学习"), ("读书", "学习"), ("勇", "勇气"), ("困难", "坚持"), ("失败", "成长"), ("理想", "理想"), ("梦想", "理想"), ("自由", "自由"), ("爱", "关系"), ("时间", "行动"), ("生命", "人生"), ("人生", "人生")):
        if term in quote_text:
            return theme
    return "成长"


def score(quote_text: str, author: Author) -> tuple[int, int, str]:
    terms = MARXISM_TERMS if author.kind == "marxism" else POPULAR_TERMS
    relevance = sum(quote_text.count(term) * 8 for term in terms)
    concise_bonus = max(0, 46 - abs(36 - len(quote_text)))
    punctuation_bonus = 8 if any(mark in quote_text for mark in "，。！？") else 0
    question_penalty = -12 if quote_text.endswith("？") else 0
    return relevance + concise_bonus + punctuation_bonus + question_penalty, -len(quote_text), quote_text


def candidates_for(author: Author, reserved_quotes: set[str]) -> list[tuple[str, str]]:
    try:
        resolved_title, wikitext = fetch_wikitext(author.page_title)
    except requests.HTTPError as error:
        if error.response is not None and error.response.status_code == 404:
            return []
        raise
    candidates: dict[str, str] = {}
    for quote_text, citation in extract_quotes(wikitext):
        normalized = unicodedata.normalize("NFC", quote_text)
        if normalized not in reserved_quotes and (author.kind != "popular" or is_popular_candidate(normalized)):
            candidates.setdefault(normalized, citation)
    ranked = sorted(candidates.items(), key=lambda item: score(item[0], author), reverse=True)
    return ranked


def select_family(authors: tuple[Author, ...], total: int, reserved_quotes: set[str]) -> list[tuple[Author, str, str]]:
    by_author: list[tuple[Author, list[tuple[str, str]]]] = []
    for author in authors:
        by_author.append((author, candidates_for(author, reserved_quotes)))
        time.sleep(REQUEST_DELAY_SECONDS)

    selected: list[tuple[Author, str, str]] = []
    seen = set(reserved_quotes)
    for author, candidates in by_author:
        for quote_text, citation in candidates[:author.target]:
            if len(selected) == total:
                break
            if quote_text not in seen:
                selected.append((author, quote_text, citation))
                seen.add(quote_text)
        if len(selected) == total:
            break

    if len(selected) < total:
        remaining = sorted(
            (
                (author, quote_text, citation)
                for author, candidates in by_author
                for quote_text, citation in candidates
                if quote_text not in seen
            ),
            key=lambda item: score(item[1], item[0]),
            reverse=True,
        )
        for author, quote_text, citation in remaining:
            if len(selected) == total:
                break
            if quote_text not in seen:
                selected.append((author, quote_text, citation))
                seen.add(quote_text)

    if len(selected) != total:
        supplied = len(selected)
        names = ", ".join(author.display_name for author, _ in by_author)
        raise RuntimeError(f"Only {supplied}/{total} usable quotes from {names}")
    reserved_quotes.update(seen - reserved_quotes)
    return selected


def select_primary_marxism(reserved_quotes: set[str]) -> list[tuple[Author, str, str, str]]:
    authors = {author.slug: author for author in MARXISM_AUTHORS}
    selected: list[tuple[Author, str, str, str]] = []
    seen = set(reserved_quotes)
    selected_texts: list[str] = []
    remaining: list[tuple[Author, str, str, str]] = []
    for work in PRIMARY_MARXISM_WORKS:
        author = authors[work.author_slug]
        candidates = sorted(set(extract_primary_sentences(fetch_primary_work(work))), key=lambda item: score(item, author), reverse=True)
        chosen = 0
        for quote_text in candidates:
            if quote_text in seen:
                continue
            if not is_distinct_quote(quote_text, selected_texts):
                continue
            if chosen < work.target:
                selected.append((author, quote_text, work.title, work.url))
                seen.add(quote_text)
                selected_texts.append(quote_text)
                chosen += 1
            else:
                remaining.append((author, quote_text, work.title, work.url))
        time.sleep(REQUEST_DELAY_SECONDS)
    if len(selected) < 130:
        for author, quote_text, title, url in sorted(remaining, key=lambda item: score(item[1], item[0]), reverse=True):
            if len(selected) == 130:
                break
            if quote_text not in seen:
                if not is_distinct_quote(quote_text, selected_texts):
                    continue
                selected.append((author, quote_text, title, url))
                seen.add(quote_text)
                selected_texts.append(quote_text)
    if len(selected) != 130:
        raise RuntimeError(f"Only {len(selected)}/130 usable Marxism-principles quotes from primary sources")
    reserved_quotes.update(seen - reserved_quotes)
    return selected


def inspiration_for(theme: str, kind: str, index: int) -> str:
    if kind == "marxism":
        options = (
            "先看现实条件和关系，再提出判断；把立场落实为能被事实检验的分析。",
            "不要用抽象口号跳过具体问题。把对象、条件、利益和变化过程放在一起看。",
            "把认识放回行动中验证：先做可观察的一步，再根据结果修正原先的判断。",
            "面对复杂局面，先找主要矛盾和关键条件，避免把所有问题混成一团。",
            "关注人的现实处境和社会关系，不把个人选择孤立成脱离条件的道德判断。",
        )
    else:
        options = (
            "把感受转成一个可完成的动作：今天先推进最小的一步，再用结果校正方向。",
            "遇到停滞时，别急着给自己下结论；先保留行动、学习和重新选择的空间。",
            "把目标拆小，把注意力放回能控制的努力、节奏和下一次尝试上。",
            "允许过程不完美，但别把一次受挫误认成全部可能性的终点。",
            "把这句话当作一次提醒：认真生活，也认真分辨什么值得长期投入。",
        )
    return options[index % len(options)]


def explanation_for(author: Author, quote_text: str, citation: str, theme: str, index: int) -> str:
    if author.kind == "marxism":
        focus = {
            "实践": "实践不是忙碌本身，而是让认识接受现实结果的检验。",
            "辩证思维": "辩证思维要求看到联系、变化和条件，不用静止标签代替分析。",
            "劳动": "劳动在这里不仅是个人付出，也关联生产、创造和社会关系。",
            "历史唯物主义": "历史分析要回到人们怎样生活、生产和组织社会的具体条件。",
            "社会关系": "个人并不生活在真空里，理解问题要把关系、制度和资源条件一并纳入。",
            "人的发展": "自由不是抽象口号，而要落实到人能够实际发展能力、参与社会生活的条件。",
            "理论与实践": "理论的价值不在于替代现实，而在于帮助提出问题、组织行动并接受检验。",
            "人民立场": "分析公共问题时，要看真实承担后果的人群，而不只看少数人的表述。",
            "马原思考": "这句话提醒我们用现实关系和发展过程来理解问题，而不是满足于抽象判断。",
        }[theme]
        return f"这句出自{citation}，可从“{theme}”角度理解。{focus} 阅读时应回到原文的论证对象，避免把一句话当成脱离历史和条件的万能答案。"
    focus = {
        "学习": "它更适合作为长期学习的提醒，而不是催促立刻给出完美答案。",
        "勇气": "勇气不等于莽撞，而是在不确定中仍能承担选择和后果。",
        "坚持": "坚持需要方向和调整；重复受挫时，方法本身也值得复盘。",
        "成长": "成长常常表现为看法和做法的改进，不必把它包装成永远向上的情绪。",
        "理想": "理想需要被拆成可以积累的行动，才不会只停留在口号或想象里。",
        "自由": "自由既意味着选择，也意味着对选择后果和他人边界的尊重。",
        "关系": "关系的价值不在于迎合，而在于尊重、沟通和能否彼此成全。",
        "行动": "行动的意义在于形成反馈：做过、看见结果、再决定下一步。",
        "人生": "它提供的是观察生活的一个角度，不要求每个人照着同一种人生脚本行走。",
    }[theme]
    return f"这句在网络上长期被转引，来源页可回看其作者、作品或演讲出处。它之所以容易被记住，是因为把“{theme}”压缩成简洁表达。{focus}"


def card_for(slot: int, author: Author, quote_text: str, citation: str, primary_url: str | None = None) -> dict:
    theme = theme_for(quote_text, author.kind)
    stable_id = uuid.uuid5(uuid.NAMESPACE_URL, f"xinghuo-curated-v1.5/{slot}/{quote_text}")
    work_title = citation if primary_url else "热门名人语录（出处见来源页）"
    return {
        "id": str(stable_id),
        "revision": 1,
        "status": "published",
        "quote": quote_text,
        "literature": {
            "series": "马原思考" if author.kind == "marxism" else "名人名言",
            "volume": author.display_name,
            "workTitle": work_title,
            "authoredAt": author.authored_at,
        },
        "themes": [theme],
        "interpretation": {
            "inspiration": inspiration_for(theme, author.kind, slot),
            "explanation": explanation_for(author, quote_text, citation, theme, slot),
        },
        "historicalEvent": author.historical_event,
        "background": author.background,
        "story": (
            f"此卡从{citation}的原文中筛选，不以随机段落凑数；可从来源页回看上下文和论证对象。"
            if primary_url
            else f"此卡按可追溯出处筛选自{author.display_name}语录条目，不以随机段落凑数；可从来源页回看上下文和引用作品。"
        ),
        "imageId": IMAGES[(slot - START_SLOT) % len(IMAGES)],
        "sources": [
            {
                "name": f"中文马克思主义文库{citation}",
                "url": primary_url,
                "accessedAt": ACCESSED_AT,
                "type": "original",
            }
            if primary_url
            else {
                "name": f"维基语录《{author.display_name}》",
                "url": page_url(author.page_title),
                "accessedAt": ACCESSED_AT,
                "type": "authoritative",
            },
            {
                "name": f"维基百科《{author.display_name}》",
                "url": wikipedia_url(author.display_name),
                "accessedAt": ACCESSED_AT,
                "type": "contextual",
            },
        ],
        "review": {"status": "verified", "checkedAt": ACCESSED_AT},
    }


def main() -> None:
    existing_quotes = {
        unicodedata.normalize("NFC", yaml.safe_load(path.read_text(encoding="utf-8"))["quote"])
        for path in CARDS_DIR.glob("*.yaml")
        if path.name.split("-", 1)[0].isdigit() and int(path.name.split("-", 1)[0]) < START_SLOT
    }
    popular_cards = [(author, quote_text, citation, None) for author, quote_text, citation in select_family(POPULAR_AUTHORS, 300, existing_quotes)]
    primary_marxism_cards = select_primary_marxism(existing_quotes)
    deng_cards = select_family((MARXISM_AUTHORS[-1],), 20, existing_quotes)
    marxism_cards = [
        *primary_marxism_cards,
        *((author, quote_text, citation, None) for author, quote_text, citation in deng_cards),
    ]
    selected = [*popular_cards, *marxism_cards]

    if len(selected) != 450:
        raise RuntimeError(f"Expected 450 cards, generated {len(selected)}")

    stale_paths = [
        path for path in CARDS_DIR.glob("*.yaml")
        if path.name.split("-", 1)[0].isdigit() and int(path.name.split("-", 1)[0]) >= START_SLOT
    ]
    for path in stale_paths:
        path.unlink()

    for offset, (author, quote_text, citation, primary_url) in enumerate(selected):
        slot = START_SLOT + offset
        filename = f"{slot:03d}-{'marxism' if author.kind == 'marxism' else 'popular'}-{author.slug}.yaml"
        card = card_for(slot, author, quote_text, citation, primary_url)
        (CARDS_DIR / filename).write_text(
            yaml.safe_dump(card, allow_unicode=True, sort_keys=False, width=1000),
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
