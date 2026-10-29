import unittest

from generate_dashboard import Handler, build_dashboard, errors_query, latency_queries, rps_query

HANDLERS = [Handler("/items/{id}", "GET"), Handler("/items", "POST")]


class QueriesTest(unittest.TestCase):

    def test_percentiles_are_taken_over_buckets_summed_by_le(self):
        legends = [legend for legend, _ in latency_queries(HANDLERS[0])]
        self.assertEqual(["p50", "p95", "p99"], legends)
        _, p99 = latency_queries(HANDLERS[0])[2]
        self.assertEqual(
            'histogram_quantile(0.99, sum by (le) (rate(app_http_request_duration_seconds_bucket'
            '{method="GET", route="/items/{id}", datacenter=~"$datacenter", pod=~"$pod"}[$__rate_interval])))',
            p99)

    def test_rps_is_summed_over_selected_pods(self):
        self.assertEqual(
            'sum(rate(app_http_requests_total'
            '{method="POST", route="/items", datacenter=~"$datacenter", pod=~"$pod"}[$__rate_interval]))',
            rps_query(HANDLERS[1]))

    def test_errors_are_split_by_status(self):
        query = errors_query(HANDLERS[0])
        self.assertTrue(query.startswith("sum by (status) (rate(app_http_requests_total{"))
        self.assertIn('status=~"[45].."', query)


class DashboardTest(unittest.TestCase):

    def setUp(self):
        self.dashboard = build_dashboard(HANDLERS, "prom-uid", "uid", "Title")

    def test_one_row_and_three_panels_per_handler(self):
        rows = [p["title"] for p in self.dashboard["panels"] if p["type"] == "row"]
        self.assertEqual(["POST /items", "GET /items/{id}"], rows)
        self.assertEqual(len(HANDLERS) * 4, len(self.dashboard["panels"]))

    def test_dashboard_is_read_only_and_bound_to_datasource(self):
        self.assertFalse(self.dashboard["editable"])
        for panel in self.dashboard["panels"]:
            if panel["type"] == "timeseries":
                self.assertEqual("prom-uid", panel["datasource"]["uid"])

    def test_selectors_allow_multiple_values_and_all(self):
        variables = {v["name"]: v for v in self.dashboard["templating"]["list"]}
        self.assertEqual({"datacenter", "pod"}, set(variables))
        for v in variables.values():
            self.assertTrue(v["multi"])
            self.assertTrue(v["includeAll"])

    def test_panel_ids_are_unique(self):
        ids = [p["id"] for p in self.dashboard["panels"]]
        self.assertEqual(len(ids), len(set(ids)))


if __name__ == "__main__":
    unittest.main()
