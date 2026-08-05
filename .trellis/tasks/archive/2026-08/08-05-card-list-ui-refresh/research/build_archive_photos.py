from __future__ import annotations

import html
import json
import re
import time
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import yaml
from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parents[3]
IMAGE_DIR = PROJECT_ROOT / "content" / "images"
ARTIFACT_DIR = ROOT.parent / "artifacts"
API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "XinghuoArchiveResearch/1.0 (https://github.com/Hi-prof/maoxuan)"
TARGET_SIZE = (1080, 1440)
VERIFIED_AT = "2026-08-05"
ALLOWED_LICENSES = {
    "Public domain",
    "CC0",
    "CC BY 1.0",
    "CC BY 2.0",
    "CC BY 3.0",
    "CC BY 4.0",
    "CC BY-SA 1.0",
    "CC BY-SA 2.0",
    "CC BY-SA 3.0",
    "CC BY-SA 4.0",
}


@dataclass(frozen=True)
class ArchivePhoto:
    image_id: str
    series: str
    theme: str
    title: str
    creator: str
    rationale: str
    misreading_check: str
    centering: tuple[float, float] = (0.5, 0.5)


SELECTIONS = (
    ArchivePhoto(
        "archive-mao-selection-survey-masses",
        "毛泽东选集",
        "调查与群众",
        "File:Mao Tse-Tung, leader of China's Communists, addresses some of his followers. - NARA - 196235.jpg",
        "摄影者不详（美国国家档案馆藏）",
        "群众场景与人物关系清楚，适合调查、组织和群众工作主题。",
        "仅表达群众工作主题，不指认卡片内容发生在照片中的具体集会。",
        (0.48, 0.5),
    ),
    ArchivePhoto(
        "archive-mao-selection-war-army",
        "毛泽东选集",
        "战争与军队",
        "File:Waiting soldiers read newspaper01.jpg",
        "摄影者不详（朝日新闻社1938年图集）",
        "士兵阅读报纸的档案画面同时保留军队与学习语义。",
        "不把照片中的部队身份、地点或行动归于卡片所述事件。",
        (0.54, 0.5),
    ),
    ArchivePhoto(
        "archive-mao-selection-yanan-study",
        "毛泽东选集",
        "延安与学习",
        "File:中共六届六中全会主席团成员合影.jpg",
        "摄影者不详",
        "1938年延安时期的集体档案照，适合学习、整风与组织建设主题。",
        "仅用于延安时期主题，不将合影人物逐一关联到具体篇目。",
        (0.5, 0.44),
    ),
    ArchivePhoto(
        "archive-mao-selection-production",
        "毛泽东选集",
        "生产建设",
        "File:Inner Mongolia Art Troupe and delegates in Changxindian Rail Factory.jpg",
        "摄影者不详",
        "铁路工厂档案场景具有明确的生产建设与工人空间特征。",
        "仅表达建设和劳动主题，不暗示卡片发生于长辛店工厂。",
        (0.48, 0.5),
    ),
    ArchivePhoto(
        "archive-mao-selection-international",
        "毛泽东选集",
        "统一与国际",
        "File:80-G-436371 Korean War Armistice Negotiations, Panmunjom, Korea.png",
        "美国海军",
        "谈判桌与多方代表构成明确，适合国际关系、统一战线和协商主题。",
        "不把板门店谈判当作卡片篇目的直接历史现场。",
        (0.5, 0.48),
    ),
    ArchivePhoto(
        "archive-mao-selection-victory-city",
        "毛泽东选集",
        "胜利与城市",
        "File:Li Pu at Founding Ceremony of PRC.jpg",
        "摄影者不详",
        "开国典礼现场兼具胜利、群众与城市空间语义。",
        "仅用于1949年前后胜利与城市主题，不替代卡片原有史实说明。",
        (0.48, 0.44),
    ),
    ArchivePhoto(
        "archive-mao-poetry-mountains",
        "毛泽东诗词",
        "山岳",
        'File:Taoist priests in front of temple on "Sun Mountain", Changde, Hunan, China, ca.1900-1919 (IMP-YDS-RG008-358-0008-0022).jpg',
        "摄影者不详（International Mission Photography Archive）",
        "湖南山岳与人物比例鲜明，可承接登临、峰峦和远望意象。",
        "地点为常德太阳山，不冒充诗词中另有明确地名的山岳。",
        (0.5, 0.46),
    ),
    ArchivePhoto(
        "archive-mao-poetry-river",
        "毛泽东诗词",
        "江河",
        "File:A huge raft in the Yuan river, Changde, Hunan, China, ca.1900-1919 (IMP-YDS-RG008-358-0008-0047).jpg",
        "摄影者不详（International Mission Photography Archive）",
        "元江木排与水面空间适合江河、行舟和激流主题。",
        "照片明确为湖南元江，只表达江河意象，不冒充长江或湘江具体河段。",
        (0.48, 0.5),
    ),
    ArchivePhoto(
        "archive-mao-poetry-snow",
        "毛泽东诗词",
        "雪原",
        "File:Three of the porters who went highest on Everest in 1924 01.jpg",
        "摄影者不详（1924年珠峰探险档案）",
        "高海拔雪地人物提供清晰雪原和跋涉意象。",
        "仅表达雪原与跋涉，不将珠峰探险队解释为中国革命史人物。",
        (0.5, 0.43),
    ),
    ArchivePhoto(
        "archive-mao-poetry-long-march",
        "毛泽东诗词",
        "长征道路",
        "File:Mouth Of Coal Mine In Mountain Ridge West Of Ta Chu, China MAR (1909) Thomas C. Chamberlin (RESTORED) (4075544614).jpg",
        "Thomas C. Chamberlin",
        "1909年中国山脊道路的纵深适合远征、关隘和山路主题。",
        "照片早于长征且并非长征路线，只作为历史山路意象。",
        (0.54, 0.5),
    ),
    ArchivePhoto(
        "archive-mao-poetry-plum-nature",
        "毛泽东诗词",
        "梅花与自然",
        "File:Close-up of a specimen of night blooming ceres (Ce'reus), ca.1920 (CHS-5496).jpg",
        "摄影者不详（C. C. Pierce Collection）",
        "历史花卉特写保留自然、开放与枝叶层次。",
        "植物为夜开花卉而非梅花，仅映射到“自然”主题卡片。",
        (0.48, 0.45),
    ),
    ArchivePhoto(
        "archive-mao-poetry-city-change",
        "毛泽东诗词",
        "城市与时代变化",
        "File:Opening of the Wuhan Changjiang Bridge 06.jpg",
        "摄影者不详",
        "1957年武汉长江大桥开放档案照与诗词中的城市建设主题直接呼应。",
        "仅用于武汉大桥及时代建设意象，不泛化为所有城市或桥梁。",
        (0.52, 0.45),
    ),
    ArchivePhoto(
        "archive-marxism-industry",
        "马原思考",
        "工业",
        "File:StateLibQld 1 128150 Workers inside the South Brisbane Butter Factory, ca. 1900.jpg",
        "摄影者不详（昆士兰州立图书馆藏）",
        "工厂内部、设备和劳动者同框，工业生产关系清晰。",
        "仅表达一般工业空间，不关联中国或某一理论文本的具体案例。",
        (0.46, 0.5),
    ),
    ArchivePhoto(
        "archive-marxism-labor",
        "马原思考",
        "劳动者",
        "File:StateLibQld 1 52836 Workers in a boot making factory, South Brisbane, 1900.jpg",
        "《The Queenslander》摄影资料",
        "制鞋工人群像适合劳动分工、协作与生产主题。",
        "不把澳大利亚工厂当作卡片所述社会的直接案例。",
        (0.52, 0.5),
    ),
    ArchivePhoto(
        "archive-marxism-books",
        "马原思考",
        "书籍手稿",
        "File:Fisher Fine Arts Library Reading Room 1900.png",
        "摄影者不详",
        "阅览室的书架、桌面与阅读者形成明确知识生产空间。",
        "作为阅读和理论学习主题，不指向某一本具体著作。",
        (0.52, 0.48),
    ),
    ArchivePhoto(
        "archive-marxism-city-society",
        "马原思考",
        "城市社会",
        "File:Photograph of the Newspapers & Current Periodicals Reading Room, Library of Congress LCCN2002716645.jpg",
        "美国国会图书馆",
        "报刊阅览空间体现信息传播、公共生活和城市社会。",
        "仅表示公共信息空间，不对应卡片中的具体国家和年代。",
        (0.5, 0.46),
    ),
    ArchivePhoto(
        "archive-marxism-international-movement",
        "马原思考",
        "国际运动",
        "File:Women Picket during Ladies Tailors Strike, 02-1910 (11192045794).jpg",
        "美国国家档案馆",
        "1910年女裁缝罢工纠察档案可表达劳动运动与集体行动。",
        "明确是美国劳工史场景，不代指其他国家的运动。",
        (0.48, 0.48),
    ),
    ArchivePhoto(
        "archive-marxism-speech-organization",
        "马原思考",
        "演讲与组织",
        "File:Crowd of strikers going to a meeting, Philadelphia LCCN2014684530.jpg",
        "Bain News Service",
        "前往会议的罢工者群体适合组织、会议和公共行动主题。",
        "明确是费城劳工运动场景，仅承载一般组织主题。",
        (0.5, 0.45),
    ),
    ArchivePhoto(
        "archive-quotes-reading-writing",
        "名人名言",
        "阅读写作",
        "File:Rotunda Reading Room ca1900 LOC 15371v.jpg",
        "摄影者不详（美国国会图书馆藏）",
        "圆形阅览室的人物、书桌和书架清楚，适合阅读、写作和学习主题。",
        "不将阅览室人物认作卡片名言作者。",
        (0.5, 0.46),
    ),
    ArchivePhoto(
        "archive-quotes-science",
        "名人名言",
        "科学探索",
        "File:Physical chemistry laboratory at the University of Leipzig - DPLA - 8f202bf3ff93444dfb1cee6ff73405d9.jpg",
        "摄影者不详（莱比锡大学档案）",
        "物理化学实验室设备与研究空间适合科学、实验和求真主题。",
        "不将实验室人物认作卡片作者或特定科学家。",
        (0.54, 0.48),
    ),
    ArchivePhoto(
        "archive-quotes-public-speech",
        "名人名言",
        "公共演讲",
        "File:Martin Luther King - March on Washington.jpg",
        "Rowland Scherman",
        "华盛顿大游行演讲现场具有明确公共表达与倾听关系。",
        "仅映射公共演讲主题；非马丁·路德·金相关卡片不暗示作者身份。",
        (0.5, 0.42),
    ),
    ArchivePhoto(
        "archive-quotes-civil-rights",
        "名人名言",
        "公民权利",
        "File:Photograph of Meeting with Leaders of the March on Washington August 28, 1963 - NARA - 194276.jpg",
        "美国白宫摄影办公室",
        "权利运动领袖会面档案适合平等、公民责任和社会行动主题。",
        "明确是1963年华盛顿大游行相关会面，不代指其他权利事件。",
        (0.5, 0.42),
    ),
    ArchivePhoto(
        "archive-quotes-journey",
        "名人名言",
        "远行",
        "File:Mount Lowe Railway car at Granite Gate, ca.1900 (CHS-2180).jpg",
        "C. C. Pierce",
        "山地铁路车辆与峭壁道路适合远行、选择和探索主题。",
        "明确是美国洛杉矶地区历史铁路，不对应卡片作者的真实旅程。",
        (0.52, 0.48),
    ),
    ArchivePhoto(
        "archive-quotes-nature",
        "名人名言",
        "自然",
        "File:Adams The Tetons and the Snake River.jpg",
        "Ansel Adams",
        "山脉、河流和开阔空间适合自然、视野和长期主义主题。",
        "明确是美国提顿山与蛇河，不作为卡片人物经历或中国地点。",
        (0.5, 0.47),
    ),
)


def plain_text(value: str | None) -> str:
    if not value:
        return ""
    text = re.sub(r"<[^>]+>", " ", value)
    return " ".join(html.unescape(text).split())


def request_bytes(url: str, *, data: bytes | None = None, retries: int = 5) -> bytes:
    for attempt in range(retries):
        request = Request(url, data=data, headers={"User-Agent": USER_AGENT})
        try:
            with urlopen(request, timeout=90) as response:
                return response.read()
        except HTTPError as error:
            if error.code != 429 or attempt == retries - 1:
                raise
        except URLError:
            if attempt == retries - 1:
                raise
        time.sleep(3 * (attempt + 1))
    raise RuntimeError("unreachable")


def load_metadata() -> dict[str, dict]:
    payload = {
        "action": "query",
        "titles": "|".join(selection.title for selection in SELECTIONS),
        "prop": "imageinfo",
        "iiprop": "url|size|extmetadata",
        "iiurlwidth": "1800",
        "format": "json",
        "formatversion": "2",
    }
    response = json.loads(request_bytes(API, data=urlencode(payload).encode()).decode())
    pages = response.get("query", {}).get("pages", [])
    metadata: dict[str, dict] = {}
    for page in pages:
        info = page.get("imageinfo", [{}])[0]
        extmetadata = info.get("extmetadata", {})
        license_name = plain_text(extmetadata.get("LicenseShortName", {}).get("value"))
        width = int(info.get("width", 0))
        height = int(info.get("height", 0))
        if license_name not in ALLOWED_LICENSES:
            raise ValueError(f"Unsupported license for {page['title']}: {license_name}")
        if min(width, height) < 720:
            raise ValueError(f"Source image too small for {page['title']}: {width}x{height}")
        source_url = info.get("descriptionurl", "")
        license_url = plain_text(extmetadata.get("LicenseUrl", {}).get("value"))
        metadata[page["title"]] = {
            "artist": plain_text(extmetadata.get("Artist", {}).get("value")) or "Unknown",
            "license": license_name,
            "licenseEvidence": license_url or f"{source_url}#Licensing",
            "sourceUrl": source_url,
            "downloadUrl": info.get("thumburl", info.get("url", "")),
            "width": width,
            "height": height,
        }
    missing = [selection.title for selection in SELECTIONS if selection.title not in metadata]
    if missing:
        raise ValueError(f"Commons entries missing: {missing}")
    return metadata


def build_photo(selection: ArchivePhoto, metadata: dict) -> None:
    source = Image.open(BytesIO(request_bytes(metadata["downloadUrl"])))
    source = ImageOps.exif_transpose(source).convert("RGB")
    fitted = ImageOps.fit(
        source,
        TARGET_SIZE,
        method=Image.Resampling.LANCZOS,
        centering=selection.centering,
    )
    output_path = IMAGE_DIR / f"{selection.image_id}.jpg"
    fitted.save(output_path, "JPEG", quality=88, optimize=True, progressive=True)

    image_yaml = {
        "id": selection.image_id,
        "file": output_path.name,
        "sourceUrl": metadata["sourceUrl"],
        "creator": selection.creator,
        "license": metadata["license"],
        "licenseEvidence": metadata["licenseEvidence"],
        "verifiedAt": VERIFIED_AT,
        "shareAllowed": True,
    }
    (IMAGE_DIR / f"{selection.image_id}.yaml").write_text(
        yaml.safe_dump(image_yaml, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


def write_manifest(metadata: dict[str, dict]) -> None:
    lines = [
        "# 历史档案照片清单",
        "",
        f"- 核验日期：`{VERIFIED_AT}`",
        "- 处理方式：从 Wikimedia Commons 条目取得原图或 1800 px 缩略图，居中/定点裁切为 `1080 x 1440` JPEG；不烘焙文字、纹理或渐变。",
        "- 映射原则：图片只承担系列与主题氛围，不能替代卡片内的历史节点和来源说明。",
        "",
        "| 系列 | 主题 | 图片 ID | Commons 条目 | 作者/机构 | 许可 | 选择理由 | 潜在误读检查 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for selection in SELECTIONS:
        item = metadata[selection.title]
        title = selection.title.removeprefix("File:").replace("|", "\\|")
        source_link = f"[{title}]({item['sourceUrl']})"
        license_link = f"[{item['license']}]({item['licenseEvidence']})"
        lines.append(
            "| "
            + " | ".join(
                (
                    selection.series,
                    selection.theme,
                    f"`{selection.image_id}`",
                    source_link,
                    selection.creator,
                    license_link,
                    selection.rationale,
                    selection.misreading_check,
                )
            )
            + " |"
        )
    (ROOT / "archive-photo-manifest.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_contact_sheet() -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    columns = 4
    cell_width, cell_height = 270, 390
    rows = (len(SELECTIONS) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_width, rows * cell_height), "white")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, selection in enumerate(SELECTIONS):
        column, row = index % columns, index // columns
        x, y = column * cell_width, row * cell_height
        image = Image.open(IMAGE_DIR / f"{selection.image_id}.jpg").convert("RGB")
        preview = ImageOps.fit(image, (250, 334), method=Image.Resampling.LANCZOS)
        sheet.paste(preview, (x + 10, y + 8))
        label = selection.image_id.replace("archive-", "")
        draw.text((x + 10, y + 350), label, fill="black", font=font)
    sheet.save(ARTIFACT_DIR / "archive-photo-contact-sheet.jpg", quality=90)


def main() -> None:
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    metadata = load_metadata()
    for index, selection in enumerate(SELECTIONS, start=1):
        print(f"[{index:02d}/{len(SELECTIONS)}] {selection.image_id}")
        build_photo(selection, metadata[selection.title])
        time.sleep(0.75)
    write_manifest(metadata)
    write_contact_sheet()


if __name__ == "__main__":
    main()
