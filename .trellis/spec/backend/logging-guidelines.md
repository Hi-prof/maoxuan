# Logging Guidelines

## Policy

The MVP has no analytics, crash reporter, behavior tracking, or remote logging. Prefer surfaced UI errors and deterministic CLI output over persistent logs.

## Allowed Diagnostics

- CLI validation issues include the source file and field.
- Build output may include content version, card/image counts, package bytes, and SHA-256.
- Temporary local debug logs may identify an update phase or exception class, but must be removed before completion.

## Never Log

- Like, favorite, read history, current card, or reading-round order.
- Complete package bytes or complete card/source payloads.
- Access tokens, signing material, local absolute asset paths, or device identifiers.
- Network response bodies from unexpected hosts.

## Review Check

Search Android code for `Log.`/`println` and Python code for ad hoc `print` before release. The intentional Python CLI JSON/error output in `cli.py` is exempt.
