#!/usr/bin/env python3
"""Scan RF Estabelecimentos for MRO (33163) in RJ and compare with PostgreSQL."""

import os
import re
import subprocess
import sys
from pathlib import Path

RF_ROOT = Path(os.environ.get("PROSPECT_DATA_ROOT", r"C:\prospect-portal-data")) / "rf" / "extracted"
ENCODING = "latin-1"


def only_digits(value: str) -> str:
    return re.sub(r"\D", "", value or "")


def pad_left(value: str, size: int) -> str:
    d = only_digits(value)
    return d.rjust(size, "0") if d else ""


def field(cols: list[str], idx: int) -> str:
    return cols[idx] if idx < len(cols) else ""


def scan_rf() -> tuple[dict[str, str], dict[str, str]]:
    primary: dict[str, str] = {}
    secondary: dict[str, str] = {}

    for folder in sorted(RF_ROOT.glob("Estabelecimentos*")):
        if not folder.is_dir():
            continue
        files = [p for p in folder.iterdir() if "ESTABELE" in p.name.upper()]
        if not files:
            continue
        path = files[0]
        print(f"Scanning {path.name}...", flush=True)
        with path.open("r", encoding=ENCODING, errors="replace") as fh:
            for line in fh:
                cols = line.rstrip("\n\r").split(";", -1)
                if field(cols, 5) != "02":
                    continue
                if field(cols, 20).upper() != "RJ":
                    continue

                basico = pad_left(field(cols, 0), 8)
                ordem = pad_left(field(cols, 1), 4)
                dv = pad_left(field(cols, 2), 2)
                cnpj = basico + ordem + dv
                if len(cnpj) != 14:
                    continue

                cnae_main = only_digits(field(cols, 11))
                if cnae_main.startswith("33163"):
                    primary[cnpj] = cnae_main

                cnae_sec = field(cols, 12)
                if "33163" in cnae_sec and cnpj not in secondary:
                    secondary[cnpj] = cnae_sec[:120]

    return primary, secondary


def psql(query: str) -> str:
    cmd = [
        "docker", "exec", "prospect-portal-postgres",
        "psql", "-U", "prospect", "-d", "prospect_portal",
        "-t", "-A", "-c", query,
    ]
    return subprocess.check_output(cmd, text=True, encoding="utf-8", errors="replace").strip()


def main() -> int:
    if not RF_ROOT.is_dir():
        print(f"RF path not found: {RF_ROOT}", file=sys.stderr)
        return 1

    primary, secondary = scan_rf()
    sec_only = {k: v for k, v in secondary.items() if k not in primary}

    print(f"\nRF primary MRO (33163*) RJ active: {len(primary)}")
    print(f"RF secondary-only MRO RJ active: {len(sec_only)}")
    print(f"RF any MRO mention RJ active: {len(primary) + len(sec_only)}")

    if not primary:
        return 0

    cnpjs = ",".join(f"'{c}'" for c in sorted(primary))
    in_db = psql(
        f"SELECT cnpj, cnae_main FROM companies WHERE cnpj IN ({cnpjs}) ORDER BY cnpj"
    )
    db_lines = [l for l in in_db.splitlines() if l.strip()]
    db_cnpjs = {l.split("|")[0] for l in db_lines if "|" in l}

    missing = sorted(set(primary) - db_cnpjs)
    wrong_cnae = [
        l for l in db_lines
        if "|" in l and not l.split("|")[1].startswith("33163")
    ]

    print(f"In companies with primary MRO cnae: {len(db_cnpjs)}")
    print(f"Missing from companies: {len(missing)}")

    if missing:
        sample = missing[:15]
        basics = ",".join(f"'{c[:8]}'" for c in sample)
        emp = psql(
            f"SELECT cnpj_basico FROM rf_empresas WHERE cnpj_basico IN ({basics})"
        )
        emp_set = {l.strip() for l in emp.splitlines() if l.strip()}
        print("\nSample missing CNPJs (first 15):")
        for c in sample:
            has_emp = "YES" if c[:8] in emp_set else "NO"
            print(f"  {c} cnae={primary[c]} rf_empresas={has_emp}")

    if wrong_cnae:
        print(f"\nIn DB but wrong cnae_main: {len(wrong_cnae)}")
        for l in wrong_cnae[:5]:
            print(f"  {l}")

    # Export missing for targeted reimport
    out = Path(__file__).resolve().parent / "mro-rj-missing-cnpjs.txt"
    out.write_text("\n".join(missing), encoding="utf-8")
    print(f"\nWrote {len(missing)} missing CNPJs to {out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
