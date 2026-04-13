#!/bin/bash
# Analyze a PufferLib training run via wandb API.
# Usage: ./analyze_run.sh <run_id>
# Example: ./analyze_run.sh w8nsir07

if [ -z "$1" ]; then
    echo "Usage: ./analyze_run.sh <run_id>"
    exit 1
fi

RUN_ID="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/.venv/bin/activate"

python3 -c "
import wandb
api = wandb.Api()
run = api.run('jordanbaileypmp-georgia-institute-of-technology/puffer4/runs/$RUN_ID')
h = run.history(pandas=False)

print(f'Run: $RUN_ID')
print(f'Data points: {len(h)}')
print()

# Config
env = run.config.get('env', {})
print('=== CONFIG ===')
for k in sorted(env.keys()):
    print(f'  {k}: {env[k]}')
vec = run.config.get('vec', {})
print(f'  total_agents: {vec.get(\"total_agents\")}')
train = run.config.get('train', {})
print(f'  horizon: {train.get(\"horizon\")}')
print(f'  learning_rate: {train.get(\"learning_rate\")}')
print(f'  ent_coef: {train.get(\"ent_coef\")}')
print(f'  total_timesteps: {train.get(\"total_timesteps\")}')
print()

# Summary
print('=== SUMMARY ===')
s = run.summary
print(f'  SPS:            {s.get(\"SPS\", 0):.0f}')
print(f'  wave_reached:   {s.get(\"env/wave_reached\", 0):.2f}')
print(f'  max_wave:       {s.get(\"env/max_wave\", \"N/A\")}')
print(f'  most_npcs_slayed: {s.get(\"env/most_npcs_slayed\", \"N/A\")}')
print(f'  episode_length: {s.get(\"env/episode_length\", 0):.0f}')
if s.get('env/reached_wave_63', None) is not None:
    print(f'  reached_wave63: {s.get(\"env/reached_wave_63\", 0):.4f}')
if s.get('env/jad_kill_rate', None) is not None:
    print(f'  jad_kill_rate:  {s.get(\"env/jad_kill_rate\", 0):.4f}')
print(f'  epochs:         {s.get(\"epoch\", 0)}')
print(f'  entropy:        {s.get(\"loss/entropy\", 0):.3f}')
print(f'  clipfrac:       {s.get(\"loss/clipfrac\", 0):.4f}')
print(f'  value_loss:     {s.get(\"loss/value\", 0):.3f}')
print(f'  policy_loss:    {s.get(\"loss/policy\", 0):.4f}')
print(f'  kl:             {s.get(\"loss/kl\", 0):.5f}')
print(f'  uptime:         {s.get(\"uptime\", 0):.0f}s ({s.get(\"uptime\", 0)/60:.1f}m)')
print()

# Analytics (v14+)
prayer_up_magic = s.get('env/prayer_uptime_magic', None)
if prayer_up_magic is not None:
    print('=== ANALYTICS ===')
    print(f'  prayer_uptime_melee: {s.get(\"env/prayer_uptime_melee\", 0):.3f}')
    print(f'  prayer_uptime_range: {s.get(\"env/prayer_uptime_range\", 0):.3f}')
    print(f'  prayer_uptime_magic: {prayer_up_magic:.3f}')
    print(f'  correct_prayer:    {s.get(\"env/correct_prayer\", 0):.1f}')
    print(f'  dmg_taken_avg:     {s.get(\"env/dmg_taken_avg\", 0):.1f}')
    print(f'  pots_used:         {s.get(\"env/pots_used\", 0):.1f}')
    print(f'  food_eaten:        {s.get(\"env/food_eaten\", 0):.1f}')
    print()

# Progression
print('=== PROGRESSION ===')
print(f'{\"step\":>12} {\"wave\":>6} {\"ep_len\":>8} {\"entropy\":>7} {\"clip\":>6} {\"val_loss\":>9} {\"policy\":>8}')
for i in range(0, len(h), max(1, len(h)//20)):
    row = h[i]
    print(f'{row.get(\"_step\",0):>12} {row.get(\"env/wave_reached\",0):>6.1f} {row.get(\"env/episode_length\",0):>8.0f} {row.get(\"loss/entropy\",0):>7.3f} {row.get(\"loss/clipfrac\",0):>6.3f} {row.get(\"loss/value\",0):>9.3f} {row.get(\"loss/policy\",0):>8.4f}')
# Always print last row
row = h[-1]
print(f'{row.get(\"_step\",0):>12} {row.get(\"env/wave_reached\",0):>6.1f} {row.get(\"env/episode_length\",0):>8.0f} {row.get(\"loss/entropy\",0):>7.3f} {row.get(\"loss/clipfrac\",0):>6.3f} {row.get(\"loss/value\",0):>9.3f} {row.get(\"loss/policy\",0):>8.4f}')
"
