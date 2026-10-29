"""Генерирует дашборд Grafana с метриками latency, traffic и errors для всех ручек сервиса.

Список ручек берётся из GET /handlers запущенных инстансов.
Логин и пароль Grafana читаются только из окружения, чтобы не попасть в список процессов.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

LATENCY_BUCKETS = "app_http_request_duration_seconds_bucket"
LATENCY_COUNT = "app_http_request_duration_seconds_count"
REQUESTS = "app_http_requests_total"
PERCENTILES = (0.5, 0.95, 0.99)
# Фильтр по селекторам дашборда; "All" превращается в .*
SCOPE = 'datacenter=~"$datacenter", pod=~"$pod"'

PANEL_WIDTH = 8
PANEL_HEIGHT = 8


@dataclass(frozen=True, order=True)
class Handler:
    route: str
    method: str

    @property
    def title(self) -> str:
        return f"{self.method} {self.route}"

    def selector(self, extra: str = "") -> str:
        matchers = [f'method="{promql_string(self.method)}"', f'route="{promql_string(self.route)}"', SCOPE]
        if extra:
            matchers.append(extra)
        return "{" + ", ".join(matchers) + "}"


def promql_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def latency_queries(handler: Handler) -> list[tuple[str, str]]:
    # Сначала sum by (le) по выбранным подам, потом квантиль.
    buckets = f"rate({LATENCY_BUCKETS}{handler.selector()}[$__rate_interval])"
    return [(f"p{round(q * 100)}", f"histogram_quantile({q}, sum by (le) ({buckets}))") for q in PERCENTILES]


def rps_query(handler: Handler) -> str:
    return f"sum(rate({REQUESTS}{handler.selector()}[$__rate_interval]))"


def errors_query(handler: Handler) -> str:
    selector = handler.selector(extra='status=~"[45].."')
    return f"sum by (status) (rate({REQUESTS}{selector}[$__rate_interval]))"


def build_dashboard(handlers: list[Handler], datasource_uid: str, uid: str, title: str) -> dict:
    datasource = {"type": "prometheus", "uid": datasource_uid}
    panels: list[dict] = []
    y = 0
    for handler in sorted(handlers):
        panels.append(row(len(panels) + 1, handler.title, y))
        y += 1
        panels.append(timeseries(
            len(panels) + 1, "Latency p50 / p95 / p99", "s", datasource, latency_queries(handler), x=0, y=y,
            description="histogram_quantile over app_http_request_duration_seconds buckets summed by le"))
        panels.append(timeseries(
            len(panels) + 1, "Traffic, RPS", "reqps", datasource, [("RPS", rps_query(handler))],
            x=PANEL_WIDTH, y=y, description="All responses, any status"))
        panels.append(timeseries(
            len(panels) + 1, "Errors by status code, RPS", "reqps", datasource,
            [("{{status}}", errors_query(handler))], x=2 * PANEL_WIDTH, y=y, stacked=True,
            description="4xx and 5xx responses split by the exact code"))
        y += PANEL_HEIGHT
    return {
        "id": None,
        "uid": uid,
        "title": title,
        "description": "Generated from GET /handlers by dashboard-generator; changes are overwritten on restart.",
        "tags": ["http-metrics", "generated"],
        "editable": False,
        "graphTooltip": 1,
        "schemaVersion": 41,
        "time": {"from": "now-15m", "to": "now"},
        "refresh": "5s",
        "timezone": "browser",
        "templating": {"list": [
            variable("datacenter", "Datacenter", f"label_values({LATENCY_COUNT}, datacenter)", datasource),
            variable("pod", "Pod", f'label_values({LATENCY_COUNT}{{datacenter=~"$datacenter"}}, pod)', datasource),
        ]},
        "panels": panels,
    }


def variable(name: str, label: str, query: str, datasource: dict) -> dict:
    return {
        "type": "query",
        "name": name,
        "label": label,
        "datasource": datasource,
        "query": query,
        "definition": query,
        "refresh": 2,
        "multi": True,
        "includeAll": True,
        "allValue": ".*",
        "current": {"selected": True, "text": ["All"], "value": ["$__all"]},
        "options": [],
        "sort": 1,
    }


def row(panel_id: int, title: str, y: int) -> dict:
    return {"id": panel_id, "type": "row", "title": title, "collapsed": False,
            "gridPos": {"x": 0, "y": y, "w": 24, "h": 1}, "panels": []}


def timeseries(panel_id: int, title: str, unit: str, datasource: dict, queries: list[tuple[str, str]],
               x: int, y: int, description: str = "", stacked: bool = False) -> dict:
    return {
        "id": panel_id,
        "type": "timeseries",
        "title": title,
        "description": description,
        "datasource": datasource,
        "gridPos": {"x": x, "y": y, "w": PANEL_WIDTH, "h": PANEL_HEIGHT},
        "fieldConfig": {
            "defaults": {
                "unit": unit,
                "min": 0,
                "noValue": "0",
                "custom": {
                    "drawStyle": "line",
                    "lineWidth": 1,
                    "fillOpacity": 25 if stacked else 10,
                    "showPoints": "never",
                    "stacking": {"mode": "normal" if stacked else "none", "group": "A"},
                },
            },
            "overrides": [],
        },
        "options": {
            "legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
            "tooltip": {"mode": "multi", "sort": "desc"},
        },
        "targets": [
            {"refId": chr(ord("A") + i), "datasource": datasource, "expr": expr, "legendFormat": legend,
             "range": True, "editorMode": "code"}
            for i, (legend, expr) in enumerate(queries)
        ],
    }


def request_json(url: str, method: str = "GET", body: dict | None = None,
                 credentials: tuple[str, str] | None = None, timeout: float = 10) -> object:
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Accept": "application/json"})
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if credentials is not None:
        token = base64.b64encode(f"{credentials[0]}:{credentials[1]}".encode()).decode()
        request.add_header("Authorization", f"Basic {token}")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def with_retries(action, what: str, attempts: int, delay: float):
    # Повторяем сетевые ошибки и 5xx; 4xx (например, неверный пароль) сразу пробрасываем.
    for attempt in range(1, attempts + 1):
        try:
            return action()
        except urllib.error.HTTPError as error:
            if error.code < 500 or attempt == attempts:
                raise
            reason = f"HTTP {error.code}"
        except (urllib.error.URLError, OSError) as error:
            if attempt == attempts:
                raise
            reason = str(error)
        print(f"{what}: {reason}; retry {attempt}/{attempts - 1} in {delay:g}s", file=sys.stderr)
        time.sleep(delay)


def fetch_handlers(app_urls: list[str], attempts: int, delay: float) -> list[Handler]:
    # Объединяем ручки всех инстансов; ответить должен каждый.
    handlers: set[Handler] = set()
    for base in app_urls:
        url = base.rstrip("/") + "/handlers"
        payload = with_retries(lambda: request_json(url), f"GET {url}", attempts, delay)
        if not isinstance(payload, list):
            raise ValueError(f"{url}: expected a JSON array, got {type(payload).__name__}")
        for item in payload:
            if not (isinstance(item, dict) and isinstance(item.get("method"), str)
                    and isinstance(item.get("route"), str)):
                raise ValueError(f"{url}: unexpected handler entry {item!r}")
            handlers.add(Handler(route=item["route"], method=item["method"]))
        print(f"{url}: {len(payload)} handlers")
    return sorted(handlers)


def upload(grafana_url: str, credentials: tuple[str, str], dashboard: dict, attempts: int, delay: float) -> dict:
    url = grafana_url.rstrip("/") + "/api/dashboards/db"
    body = {"dashboard": dashboard, "overwrite": True, "message": "Generated from GET /handlers"}
    return with_retries(lambda: request_json(url, "POST", body, credentials), f"POST {url}", attempts, delay)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    env = os.environ.get
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--app-url", dest="app_urls", action="append",
                        help="service instance base URL, repeatable (env APP_URLS, comma-separated)")
    parser.add_argument("--grafana-url", default=env("GRAFANA_URL", "http://localhost:3000"),
                        help="Grafana base URL (env GRAFANA_URL)")
    parser.add_argument("--datasource-uid", default=env("DATASOURCE_UID", "prometheus"),
                        help="uid of the provisioned Prometheus datasource (env DATASOURCE_UID)")
    parser.add_argument("--dashboard-uid", default=env("DASHBOARD_UID", "items-http-metrics"))
    parser.add_argument("--title", default=env("DASHBOARD_TITLE", "Items service: HTTP metrics"))
    parser.add_argument("--attempts", type=int, default=30, help="attempts per HTTP call")
    parser.add_argument("--retry-delay", type=float, default=2.0, help="seconds between attempts")
    parser.add_argument("--dry-run", action="store_true", help="print the dashboard JSON instead of uploading it")
    args = parser.parse_args(argv)
    if not args.app_urls:
        args.app_urls = [u.strip() for u in env("APP_URLS", "http://localhost:8081").split(",") if u.strip()]
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        handlers = fetch_handlers(args.app_urls, args.attempts, args.retry_delay)
        if not handlers:
            print("No handlers reported by the service; nothing to show", file=sys.stderr)
            return 1
        dashboard = build_dashboard(handlers, args.datasource_uid, args.dashboard_uid, args.title)
        if args.dry_run:
            print(json.dumps(dashboard, indent=2))
            return 0
        password = os.environ.get("GRAFANA_ADMIN_PASSWORD")
        if not password:
            print("GRAFANA_ADMIN_PASSWORD is not set", file=sys.stderr)
            return 2
        credentials = (os.environ.get("GRAFANA_ADMIN_USER", "admin"), password)
        result = upload(args.grafana_url, credentials, dashboard, args.attempts, args.retry_delay)
    except urllib.error.HTTPError as error:
        hint = " (check GRAFANA_ADMIN_USER/GRAFANA_ADMIN_PASSWORD)" if error.code in (401, 403) else ""
        print(f"{error.url}: HTTP {error.code} {error.reason}{hint}", file=sys.stderr)
        return 1
    except (urllib.error.URLError, OSError, ValueError) as error:
        print(f"Dashboard generation failed: {error}", file=sys.stderr)
        return 1
    print(f"Dashboard '{args.title}' uploaded for {len(handlers)} handlers: "
          f"{', '.join(h.title for h in handlers)}; path {result.get('url')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
