from __future__ import annotations

import html
import json
import re
import sys
import textwrap
from io import BytesIO
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parent
API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "XinghuoArchiveResearch/1.0 (https://github.com/Hi-prof/maoxuan)"
PUBLIC_LICENSES = {
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

GROUPS = {
    "mao-selection": {
        "survey-masses": "Chinese peasants village 1930 photograph",
        "war-army": "Eighth Route Army soldiers photograph",
        "yanan-study": "Yan'an 1938 Mao photograph",
        "production": "Chinese factory workers 1950 photograph",
        "international": "Chinese delegation 1954 photograph",
        "victory-city": "Founding Ceremony PRC 1949 photograph",
    },
    "mao-poetry": {
        "mountains": "Huangshan 1930 photograph",
        "river": "Yangtze River 1930 photograph",
        "snow": "snow mountain China 1930 photograph",
        "long-march": "Chinese Red Army Long March photograph",
        "plum-nature": "plum blossom 1920 photograph",
        "city-change": "Wuhan Yangtze River Bridge 1957 photograph",
    },
    "marxism": {
        "industry": "factory workers 1900",
        "labor": "factory laborers 1900 photograph",
        "books": "old library reading room 1900 photograph",
        "printing": "printing press workers 1900 photograph",
        "international": "workers demonstration 1910 photograph",
        "speech": "labor speaker crowd 1910 photograph",
    },
    "quotes": {
        "writing": "writer at desk photograph",
        "theatre": "theatre audience 1900 photograph",
        "science": "scientist laboratory 1900 photograph",
        "civil-rights": "March on Washington 1963 National Archives photograph",
        "public-speech": "public speaker crowd 1930 photograph",
        "journey-nature": "railway mountain 1900 photograph",
    },
}


def plain_text(value: str | None) -> str:
    if not value:
        return ""
    text = re.sub(r"<[^>]+>", " ", value)
    return " ".join(html.unescape(text).split())


def api_json(params: dict[str, str]) -> dict:
    request = Request(f"{API}?{urlencode(params)}", headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def search(query: str) -> list[dict[str, str]]:
    payload = api_json(
        {
            "action": "query",
            "generator": "search",
            "gsrsearch": f"{query} filetype:bitmap",
            "gsrnamespace": "6",
            "gsrlimit": "40",
            "prop": "imageinfo",
            "iiprop": "url|size|extmetadata",
            "iiurlwidth": "640",
            "format": "json",
            "formatversion": "2",
        }
    )
    results: list[dict[str, str]] = []
    for page in payload.get("query", {}).get("pages", []):
        if Path(page["title"]).suffix.lower() not in {".jpg", ".jpeg", ".png"}:
            continue
        info = page.get("imageinfo", [{}])[0]
        metadata = info.get("extmetadata", {})
        license_name = plain_text(metadata.get("LicenseShortName", {}).get("value"))
        width = int(info.get("width", 0))
        height = int(info.get("height", 0))
        if license_name not in PUBLIC_LICENSES or min(width, height) < 720:
            continue
        results.append(
            {
                "title": page["title"],
                "creator": plain_text(metadata.get("Artist", {}).get("value")) or "Unknown",
                "license": license_name,
                "licenseUrl": plain_text(metadata.get("LicenseUrl", {}).get("value")),
                "sourceUrl": info.get("descriptionurl", ""),
                "originalUrl": info.get("url", ""),
                "thumbUrl": info.get("thumburl", info.get("url", "")),
                "width": width,
                "height": height,
            }
        )
        if len(results) == 4:
            break
    return results


def fetch_image(url: str) -> Image.Image:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        return Image.open(BytesIO(response.read())).convert("RGB")


def draw_contact_sheet(group: str, slots: dict[str, list[dict[str, str]]]) -> None:
    cell_width, cell_height = 320, 250
    sheet = Image.new("RGB", (cell_width * 4, cell_height * len(slots)), "white")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for row, (slot, candidates) in enumerate(slots.items()):
        for column in range(4):
            x, y = column * cell_width, row * cell_height
            if column >= len(candidates):
                draw.text((x + 8, y + 8), f"{slot}: no redistributable result", fill="black", font=font)
                continue
            candidate = candidates[column]
            try:
                image = ImageOps.fit(fetch_image(candidate["thumbUrl"]), (cell_width, 190))
                sheet.paste(image, (x, y))
            except Exception as exc:  # research output should retain metadata even if one thumbnail fails
                draw.text((x + 8, y + 8), f"thumbnail error: {exc}", fill="red", font=font)
            title = f"{slot} [{column}] {candidate['title'].removeprefix('File:')}"
            lines = textwrap.wrap(title, width=48)[:3]
            draw.multiline_text((x + 8, y + 196), "\n".join(lines), fill="black", font=font, spacing=2)
    sheet.save(ROOT / f"commons-candidates-{group}.jpg", quality=88)


def main() -> None:
    selected_groups = sys.argv[1:] or list(GROUPS)
    unknown = set(selected_groups) - set(GROUPS)
    if unknown:
        raise SystemExit(f"Unknown groups: {sorted(unknown)}")
    json_path = ROOT / "commons-candidates.json"
    output: dict[str, dict[str, list[dict[str, str]]]] = (
        json.loads(json_path.read_text(encoding="utf-8")) if json_path.exists() else {}
    )
    for group in selected_groups:
        queries = GROUPS[group]
        output[group] = {slot: search(query) for slot, query in queries.items()}
        draw_contact_sheet(group, output[group])
        json_path.write_text(
            json.dumps(output, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
