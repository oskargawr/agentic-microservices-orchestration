rootProject.name = "agentic-orchestration-thesis"

include(
    "contracts",
    "services:inventory",
    "services:payment",
    "services:shipping",
    "orchestration:platform",
    "orchestration:variant-a",
    "orchestration:worker",
    "orchestration:console",
    "experiment:oracle",
    "experiment:runner",
)
