#!/usr/bin/env python3
"""Static contract, security, and hygiene checks for Docker, Kubernetes, and scripts."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
ERRORS: list[str] = []


def check_lf_line_endings() -> None:
    patterns = ["infrastructure/**/*.sh", "infrastructure/**/*.sql", "scripts/*.sh"]
    for pattern in patterns:
        for path in ROOT.glob(pattern):
            data = path.read_bytes()
            if b"\r\n" in data:
                ERRORS.append(f"CRLF line endings found in POSIX script/SQL: {path.relative_to(ROOT)}")


def check_k8s_manifests() -> None:
    k8s_dir = ROOT / "k8s"
    secret_files = [
        p for p in (k8s_dir / "secrets").glob("*.yaml")
        if not p.name.endswith(".example.yaml")
    ]
    if secret_files:
        ERRORS.append(f"Non-example secret files present in k8s/secrets: {[p.name for p in secret_files]}")

    for yaml_path in sorted(k8s_dir.rglob("*.yaml")):
        text = yaml_path.read_text(encoding="utf-8")
        rel = yaml_path.relative_to(ROOT)
        if re.search(r"http://(localhost|127\.0\.0\.1)", text):
            ERRORS.append(f"Hardcoded localhost URL found in K8s manifest: {rel}")

        if yaml_path.parent.name == "deployments":
            for required in (
                "runAsNonRoot: true",
                "allowPrivilegeEscalation: false",
                'drop: ["ALL"]',
                "resources:",
                "limits:",
                "readinessProbe:",
                "livenessProbe:",
                "imagePullPolicy: IfNotPresent",
            ):
                if required not in text:
                    ERRORS.append(f"Missing '{required}' in K8s Deployment {rel}")
            if yaml_path.name == "api-gateway.yaml":
                if "path: /ready" in text:
                    ERRORS.append("api-gateway readinessProbe must use /health, not aggregated /ready")


def check_dockerfiles_and_compose() -> None:
    svc_df = (ROOT / "services" / "Dockerfile").read_text(encoding="utf-8")
    if "USER 1000:1000" not in svc_df:
        ERRORS.append("services/Dockerfile must run as non-root USER 1000:1000")

    fe_df = (ROOT / "frontend" / "Dockerfile").read_text(encoding="utf-8")
    if "USER 101:101" not in fe_df:
        ERRORS.append("frontend/Dockerfile must run as non-root USER 101:101")

    compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
    for secret_key in (
        "POSTGRES_PASSWORD",
        "AUTH_DB_PASSWORD",
        "RATE_DB_PASSWORD",
        "CONVERSION_DB_PASSWORD",
        "ALERT_DB_PASSWORD",
        "NOTIFICATION_DB_PASSWORD",
        "RABBITMQ_PASSWORD",
        "JWT_SECRET",
    ):
        if f"${{{secret_key}:?" not in compose:
            ERRORS.append(f"docker-compose.yml must enforce required env var {secret_key} via ':?'")


def main() -> int:
    check_lf_line_endings()
    check_k8s_manifests()
    check_dockerfiles_and_compose()
    if ERRORS:
        print("CONTRACT VALIDATION FAILED:")
        for err in ERRORS:
            print(f"  - {err}")
        return 1
    print("ALL STATIC SECURITY, K8S, DOCKER, AND LINE-ENDING CONTRACTS PASSED.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
