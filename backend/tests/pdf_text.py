"""Texto de un PDF de fpdf2 para las pruebas, sin dependencias: descomprime cada flujo y junta lo que escribe."""
import re
import zlib


def pdf_text(data: bytes) -> str:
    lines = []
    for raw in re.findall(rb"stream\r?\n(.*?)\r?\nendstream", data, re.S):
        try:
            content = zlib.decompress(raw)
        except zlib.error:
            continue
        for array, literal in re.findall(rb"\[(.*?)\]\s*TJ|\((.*?)(?<!\\)\)\s*Tj", content, re.S):
            parts = re.findall(rb"\((.*?)(?<!\\)\)", array) if array else [literal]
            lines.append(re.sub(rb"\\([()\\])", rb"\1", b"".join(parts)).decode("latin-1"))
    return "\n".join(lines)
