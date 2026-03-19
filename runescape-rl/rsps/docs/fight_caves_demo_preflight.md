# Fight Caves Demo Preflight

The RSPS-backed Fight Caves behavioral tests depend on the standard RSPS cache being present at:

- `/home/jordan/code/RSPS/data/cache`

At minimum, that directory must contain `main_file_cache.dat2` and the matching `main_file_cache.idx*` files. If the cache is missing, `WorldTest`-based suites will fail during bootstrap before any Fight Caves assertions run.

This matters for:

- `content.area.karamja.tzhaar_city.FightCaveEpisodeInitializerTest`
- `content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- any other `WorldTest`-based RSPS game test

In this workspace, the existing headless cache can be mounted into RSPS with a symlink:

```bash
ln -s /home/jordan/code/fight-caves-RL/data/cache /home/jordan/code/RSPS/data/cache
```

Recommended preflight before rerunning the Fight Caves suite:

```bash
test -f /home/jordan/code/RSPS/data/cache/main_file_cache.dat2
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RSPS
./gradlew --no-daemon :game:test --tests content.area.karamja.tzhaar_city.FightCaveEpisodeInitializerTest --tests content.area.karamja.tzhaar_city.TzhaarFightCaveTest
```
