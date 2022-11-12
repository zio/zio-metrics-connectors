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
        "metrics/zmx-metric-reference",
        "metrics/statsd-client",
        "metrics/prometheus-client",
        "metrics/instrumentation-examples",
      ]
    }
  ]
};

module.exports = sidebars;
