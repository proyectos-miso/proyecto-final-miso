"""Base de pruebas; otro nombre por worktree permite correr dos suites a la vez."""
import os

TEST_DATABASE = os.environ.get("TEST_DATABASE_NAME", "solventa_test")
