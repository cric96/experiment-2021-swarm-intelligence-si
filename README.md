# Experiment for Clustering in Aggregate Computing

This repository contains the first experiment that explores the clustering techniques
applied in Aggregate Computing.

This work starts from this [idea](https://github.com/metaphori/paper-2021-swarm-intelligence-si/blob/master/_Brainstorming/algorithm1.txt)

In [Experiments](#experiments) I briefly discuss the current state of the simulation performed.
Each entry has the form of: 
### Name 
> simulation file. 

For run a simulation, you can write ./gradlew run<SimulationFile> 
(I leave the command at the end of each section).
The experiment body is structured as:
- current status: (:+1: ok, clustering seems to work; :-1: ko, clustering do not find good clusters; :open_hands: the experiment lead to no conclusion)
- a brief description of the simulation setup (nodes, clusters to find, ...)
- simulation snapshots
In [Todo](#todo) is present the relevant works that will have to do.

In [Problems](#problems) I underline the limitations/current problem that we have discovered.
## Experiments

### Gaussian Distributions
> *standardPatterns*

:+1:

In this experiment, there are five disjointed clusters that follows a gaussian distribution.

```
./gradlew runStandardPatternsGraphic 
```


### Gaussian Overlays
> *overlayPatterns*

:+1:

In this experiment, there are four joined clusters that follow a gaussian distribution.
Moreover, there is one disjointed cluster from the others.


```
./gradlew runOverlayPatternsGraphic 
```

### Stretched Gaussian Distribution 
> *stretchedOutPatterns*

:+1:

In this experiment, there are four disjointed clusters with a gaussian distribution but stretched.

```
./gradlew runStretchedOutPatternsGraphic 
```

### Uniform Layers Distribution
> *uniformLayers*

:-1:

In this experiment, there is one cluster with a uniform value (convex, a rectangle).
Due to the fact the cluster is uniform, the candidate selection goes wrong, and the program finds more pattern (3). 
```
./gradlew runUniformLayersGraphic 
```

### Non-Convex Layers Distribution 
> *nonConvexLayers*

:-1:

This experiment is similar to [Uniform Layers Distribution](#uniform-layers-distribution), but the cluster share is non-convex.
So, as the previous example, the program cannot find correct cluster distribution.
```
./gradlew runUniformLayersGraphic 
```


## Todo
- [ ] Use a metric to evaluate the cluster created with Aggregate Computing (see related works for that)
- [ ] Find a way to plot different clusters (currently you need to inspect each node)
- [ ] Try to generalise the cluster formation (i.e. candidate selection, in condition, out condition, live condition)
- [ ] Try to create cluster using G and C (link to the previous work)

## Problems
- Initially, there is a lot of noise due to candidate selection.
- `W` parameter is quite important. With wrong W the Aggregate Program find bad clusters.
