const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    "how-it-works",
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        "reference/metric-reference",
        {
          type: "category",
          label: "Connectors",
          collapsed: false,
          items: [
            "reference/prometheus-client",
            "reference/statsd-client",
            "reference/datadog-client",
            "reference/new-relic-client",
            "reference/micrometer-connector",
          ],
        },
        "reference/configuration",
        "reference/metric-type-mapping",
      ],
    },
    "tutorial/instrument-and-scrape",
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to/choose-a-connector",
        "how-to/instrument-an-application",
        "how-to/add-labels",
        "how-to/serve-prometheus-metrics",
        "how-to/visualize-metrics-in-grafana",
        "how-to/send-metrics-to-datadog",
        "how-to/report-runtime-and-jvm-metrics",
        "how-to/migrate-from-deprecated-layers",
        "how-to/test-metrics-locally",
      ],
    },
  ],
};

module.exports = sidebars;
