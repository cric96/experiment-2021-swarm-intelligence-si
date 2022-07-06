#!/bin/bash

printf '[\33[01;32m  gaussian  \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "standardPatterns_seed*" standard ./data/img/standard "Scenario 1: "
python plots/plotter.py plots/errors.yml data "standardPatterns_seed*" standard-errors ./data/img/standard-errors "Scenario 1: "
python plots/plotter.py plots/cluster-node-count-five.yml data "standardPatterns_seed*" standard-count ./data/img/standard-count "Scenario 1: "
python plots/plotter.py plots/metrics.yml data "standardPatterns_seed*" standard-metrics ./data/img/standard-metrics "Scenario 1: "

printf '[\33[01;32m  stretched  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "stretched*" stretched ./data/img/stretched "Scenario 2: "
python plots/plotter.py plots/errors.yml data "stretched*" stretched-errors ./data/img/stretched-errors "Scenario 2: "
python plots/plotter.py plots/cluster-node-count-four.yml data "stretched*" stretched-count ./data/img/stretched-count "Scenario 2: "
python plots/plotter.py plots/metrics.yml data "stretched*" stretched-metrics ./data/img/stretched-metrics "Scenario 2: "

printf '[\33[01;32m  one direction  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionField_seed*" one-direction ./data/img/one-direction "Scenario 3-c: "
python plots/plotter.py plots/metrics.yml data "oneDirectionField_seed*" one-direction-metrics ./data/img/one-direction-metrics "Scenario 3-c: "
python plots/plotter.py plots/cluster-node-count-one.yml data "oneDirectionField_seed*" one-direction-count ./data/img/one-direction-count "Scenario 3-c: "

printf '[\33[01;32m  one direction with local minimum  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionFieldLocal*" one-direction-local ./data/img/one-direction-local "Scenario 3-d: "
python plots/plotter.py plots/metrics.yml data "oneDirectionFieldLocal*" one-direction-local-metrics ./data/img/one-direction-local-metrics "Scenario 3-d: "

printf '[\33[01;32m  overlay \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "overlay*" overlay ./data/img/overlay "Scenario 4: "
python plots/plotter.py plots/cluster-node-count-five.yml data "overlay*" overlay-count ./data/img/overlay-count "Scenario 4: "
python plots/plotter.py plots/errors.yml data "overlay*" overlay-errors ./data/img/overlay-errors "Scenario 4: "
python plots/plotter.py plots/metrics.yml data "overlay*" overlay-metrics ./data/img/overlay-metrics "Scenario 4: "

printf '[\33[01;32m  non convex  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "nonConvex*" non-convex ./data/img/non-convex "Scenario 5: "
python plots/plotter.py plots/metrics.yml data "nonConvex*" non-convex-metrics ./data/img/non-convex-metrics "Scenario 5: "
python plots/plotter.py plots/cluster-node-count-two.yml data "nonConvex*" nonConvex-count ./data/img/nonConvex-count "Scenario 5: "

printf '[\33[01;32m  movement \33[01;37m]\n'
python plots/plotter.py plots/config-movement.yml data "standardPatternsMovement*" movement ./data/img/movement "Scenario 6: "
python plots/plotter.py plots/cluster-node-count-movement.yml data "standardPatternsMovement*" movement-count ./data/img/movement-count "Scenario 6: "
python plots/plotter.py plots/errors-movement.yml data "standardPatternsMovement*" movement-errors ./data/img/movement-errors "Scenario 6: "
python plots/plotter.py plots/metrics-movement.yml data "standardPatternsMovement*" movement-metrics ./data/img/movement-metrics "Scenario 6: "

printf '[\33[01;32m  updatable field  \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "standardPatternsUpdate*" standard-updatable ./data/img/standard-updatable "Scenario 7: "
python plots/plotter.py plots/errors.yml data "standardPatternsUpdate*" standard-updatable-errors ./data/img/standard-updatable-errors "Scenario 7: "
python plots/plotter.py plots/cluster-node-count-four.yml data "standardPatternsUpdate*" standard-updatable-count ./data/img/standard-updatable-count "Scenario 7: "
python plots/plotter.py plots/metrics.yml data "standardPatternsUpdate*" standard-updatable-metrics ./data/img/standard-updatable-metrics "Scenario 7: "

printf '[\33[01;32m  uniform  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "uniformLayers*" uniform uniform ./data/img/uniform "Scenario 8: "
python plots/plotter.py plots/metrics.yml data "uniformLayers*" uniform-metrics uniform-metrics ./data/img/uniform  "Scenario 8: "

printf '[\33[01;32m  failures  \33[01;37m]\n'
python failurePlot.py