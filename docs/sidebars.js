const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "Getting Started",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: ["getting-started"],
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      link: { type: "doc", id: "metrics/index" },
      items: [
        "metrics/metric-reference",
        {
          type: "category",
          label: "Connectors",
          collapsed: false,
          items: [
            "metrics/prometheus-client",
            "metrics/statsd-client",
            "metrics/datadog-client",
            "metrics/new-relic-client",
            "metrics/micrometer-connector",
          ],
        },
        "reference/configuration",
        "reference/metric-type-mapping",
        "metrics/instrumentation-examples",
      ],
    },
    {
      type: "category",
      label: "Tutorial",
      collapsed: false,
      items: ["tutorial/instrument-and-scrape"],
    },
    {
      type: "category",
      label: "How-to Guides",
      collapsed: false,
      items: [
        "how-to/serve-prometheus-metrics",
        "how-to/add-labels",
        "how-to/choose-a-connector",
        "how-to/test-metrics-locally",
      ],
    },
  ],
};

module.exports = sidebars;
