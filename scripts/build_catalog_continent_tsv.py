#!/usr/bin/env python3
"""Génère catalog-continent-iso3.tsv (alpha-3 + clé continent) depuis iso3166-all.csv."""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
src = ROOT / "src/main/resources/esim/iso3166-all.csv"
out = ROOT / "src/main/resources/esim/catalog-continent-iso3.tsv"

def main():
    by_key: dict[str, set[str]] = {k: set() for k in [
        "AFRICA", "EUROPE", "ASIA", "OCEANIA", "ANTARCTICA",
        "NORTH_AMERICA", "SOUTH_AMERICA", "CENTRAL_AMERICA", "CARIBBEAN",
        "MIDDLE_EAST", "AMERICAS",
    ]}
    with src.open(newline="", encoding="utf-8") as f:
        rd = csv.reader(f)
        header = next(rd)
        ix = {name.strip(): i for i, name in enumerate(header)}
        ia3 = ix["alpha-3"]
        ireg = ix["region"]
        isub = ix["sub-region"]
        iinter = ix["intermediate-region"]
        for row in rd:
            if len(row) <= ia3:
                continue
            a3 = (row[ia3] or "").strip()
            if not a3:
                continue
            reg = (row[ireg] or "").strip()
            sub = (row[isub] or "").strip()
            inter = (row[iinter] if len(row) > iinter else "") or ""
            inter = inter.strip()
            if a3 == "ATA":
                by_key["ANTARCTICA"].add(a3)
                continue
            if reg == "Africa":
                by_key["AFRICA"].add(a3)
            elif reg == "Europe":
                by_key["EUROPE"].add(a3)
            elif reg == "Asia":
                by_key["ASIA"].add(a3)
                if "Western Asia" in sub:
                    by_key["MIDDLE_EAST"].add(a3)
            elif reg == "Oceania":
                by_key["OCEANIA"].add(a3)
            elif reg == "Americas":
                if inter == "Caribbean":
                    by_key["CARIBBEAN"].add(a3)
                elif inter == "Central America":
                    by_key["CENTRAL_AMERICA"].add(a3)
                elif inter == "South America":
                    by_key["SOUTH_AMERICA"].add(a3)
                elif "Northern America" in sub:
                    by_key["NORTH_AMERICA"].add(a3)
                elif inter:
                    by_key["AMERICAS"].add(a3)
                else:
                    by_key["AMERICAS"].add(a3)
                by_key["AMERICAS"].add(a3)
    lines: list[str] = []
    for key, codes in sorted(by_key.items()):
        for c in sorted(codes):
            lines.append(f"{c}\t{key}\n")
    out.write_text("".join(lines), encoding="utf-8")
    print(f"Wrote {len(lines)} lines to {out}")

if __name__ == "__main__":
    main()
