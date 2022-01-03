# A Field-based Computing Approach to Sensing-driven Clustering in Robot Swarms
[![DOI](https://zenodo.org/badge/434830188.svg)](https://zenodo.org/badge/latestdoi/434830188)

This repository contains the experiments that explore sensing-driven clustering techniques applied in Aggregate Computing.

In [result discussion](./RESULT-BRIEF-DISCUSSION.md) there is a legend that explain how to interpret the graphical simulations.

## Prerequisite
A working version of Java, the supported version range is 11 to 17, and a working version of Python 3, including pip.

## How to launch
To run the Alchemist simulations devised from this paper, you could
use the pre-defined gradle task `runAllGraphic`.
This task will launch each simulation defined by a yaml file in `src/main/yaml`.

Hence, to run all the simulation you have to write
```bash
./gradlew runAllGraphic
```
Press <kb>P</kb> to start the simulation.

For further information about the GUI, see the [graphical interface shortcuts](https://alchemistsimulator.github.io/wiki/usage/gui/).

In Windows, I suggest you to use WSL 2. Otherwise, you should write:
```bash
gradlew.bat runAllGraphic
```
## Re-generating all the data
The experiment is entirely reproducible.
Regenerating all the data may take *weeks* on a well-equipped 2021 personal computer.
The process is CPU-intensive and we do not recommend running it on devices where heat can be damaging to the battery.

In order to re-run all the experiments, launch:
```bash
./gradlew runAllBatch
```
data will be generated into the `data` folder
## Reproduce plots
If you are just willing to re-run the data analysis and generate all the charts in the paper (plus hundreds other),
you can use the data we generated in the past:

```bash
pip install -r requirements.txt --user
./plot-all-clusters.sh
```
