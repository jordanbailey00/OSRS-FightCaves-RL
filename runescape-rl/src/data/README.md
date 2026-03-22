# src/data/

Placeholder for wandb, experiment dashboards, and analytics/reporting integrations.

Currently, wandb/logging code lives inside `src/rl/fight_caves_rl/logging/` as part of the RL Python package. It remains there because extracting it would require import changes that cross the "no logic changes" boundary of the reorg PR.

When this module is populated, it should own:
- Wandb integration and experiment tracking
- Training dashboards and visualization
- Analytics pipelines and reporting
- Experiment comparison tooling
