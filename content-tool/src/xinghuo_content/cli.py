from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .builder import BuildError, build_package
from .report import write_content_report
from .validator import ValidationError, validate_content


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="xinghuo-content")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate", help="validate content sources")
    validate.add_argument("content", type=Path)
    validate.add_argument("--formal", action="store_true")

    build = subparsers.add_parser("build", help="build deterministic release assets")
    build.add_argument("content", type=Path)
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--bootstrap-output", type=Path)
    build.add_argument("--formal", action="store_true")
    build.add_argument("--verify-deterministic", action="store_true")

    report = subparsers.add_parser("report", help="write a formal content review report")
    report.add_argument("content", type=Path)
    report.add_argument("--output", type=Path, required=True)
    report.add_argument("--formal", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "validate":
            content = validate_content(args.content, formal=args.formal)
            print(
                json.dumps(
                    {
                        "version": content.project.content_version,
                        "publishedCards": len(content.published_cards),
                        "withdrawals": len(content.withdrawals),
                        "images": len(content.images),
                    },
                    ensure_ascii=False,
                )
            )
            return 0
        if args.command == "build":
            result = build_package(
                args.content,
                args.output,
                formal=args.formal,
                bootstrap_output=args.bootstrap_output,
                verify_deterministic=args.verify_deterministic,
            )
            print(json.dumps(result, ensure_ascii=False))
            return 0
        content = validate_content(args.content, formal=args.formal)
        write_content_report(content, args.output)
        print(json.dumps({"report": str(args.output)}, ensure_ascii=False))
        return 0
    except ValidationError as exc:
        for issue in exc.issues:
            print(f"ERROR: {issue}", file=sys.stderr)
        return 2
    except BuildError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 3
