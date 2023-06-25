const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    {
      type: "category",
      label: "Metrics",
      collapsed: true,
      link: { type: "doc", id: "metrics/index" },
      items: [
        "metrics/metric-reference",
        "metrics/statsd-client",
        "metrics/prometheus-client",
        "metrics/micrometer-connector",
        "metrics/instrumentation-examples",
      ]
    }
  ]
};

module.exports = sidebars;
