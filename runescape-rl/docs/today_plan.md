# Today Plan — 2026-04-09

IMPORTANT STUFF
v21: v4ekkk3z (need to do analysis and post results in rl_config, make sure score is correct. did we kill jad or no?)

## 0. v21 

- do analysis and post results
- figure out if the jad analytics works to determine if we killed jad or not
- do replay in eval viewer
- setup for v21.1

## 0.1 Runpod setup

- get everything working and functional in runpod so all we have to do when we spin up an env is ssh into it and run train


## 1. Decouple anneal horizon

Primary Objective:
- decouple anneal horizon so we can do a long run of v22 (mostly same config as v20.2 and v21).


## 2. Codebase organization

- cleanup entire codebase
- update all docs
- ensure everything is easy and painless to use
- also want to organize it so we can add additional OSRS modules to it seamlessly. fight caves will be just one module
- generate README with info about the effort, results, screenshots/images instructions on how to download, setup, and use


## 3. Training-Env Performance Refactor

Primary objective:
- refactor `training-env` specifically for performance improvements
- use gprof
- use bash build.sh ENV_NAME --profile


## 4. Blog post

- in depth discussion of what the project is, how it evolved, what i tried, what i built, etc with screenshots and results and details. make it a journey from where i started to where I am now. discuss the engineering and architecture


## 5. Full OSRS sim

- start building full OSRS sim in RayLib. follow the exact same steps we did for fight caves but for all of OSRS. maybe do a whole city, then a whole region, and work our way up. 
- goal is to be able to have most (if not all) functionality and train agents on various things like skilling, PVM, questing, etc. 
