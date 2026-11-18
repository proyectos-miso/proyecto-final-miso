"""Base de pruebas; otro nombre por worktree permite correr dos suites a la vez."""
import os
import re

TEST_DATABASE = os.environ.get("TEST_DATABASE_NAME", "solventa_test")

# El nombre va en un CREATE DATABASE: solo minúsculas, dígitos y guion bajo.
if not re.fullmatch(r"[a-z_][a-z0-9_]*", TEST_DATABASE):
    raise ValueError(
        "TEST_DATABASE_NAME debe tener solo minúsculas, dígitos y guion bajo, y no empezar por un dígito."
    )
