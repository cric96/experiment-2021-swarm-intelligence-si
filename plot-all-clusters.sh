#!/bin/bash

printf '[\33[01;32m  gaussian  \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "standardPatterns_seed*" standard standard
python plots/plotter.py plots/errors.yml data "standardPatterns_seed*" standard-errors standard-errors
python plots/plotter.py plots/cluster-node-count.yml data "standardPatterns_seed*" standard-count standard-count
python plots/plotter.py plots/metrics.yml data "standardPatterns_seed*" standard-metrics standard-metrics
printf '[\33[01;32m  non convex  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "nonConvex*" non-convex non-convex
python plots/plotter.py plots/metrics.yml data "nonConvex*" non-convex-metrics non-convex-metrics
python plots/plotter.py plots/cluster-node-count.yml data "nonConvex*" nonConvex-count nonConvex-count
printf '[\33[01;32m  one direction  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionField_seed*" one-direction one-direction
python plots/plotter.py plots/metrics.yml data "oneDirectionField_seed*" one-direction-metrics one-direction-metrics
python plots/plotter.py plots/cluster-node-count.yml data "oneDirectionField_seed*" one-direction-count one-direction-count
printf '[\33[01;32m  one direction with local minimum  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionFieldLocal*" one-direction-local one-direction-local
python plots/plotter.py plots/metrics.yml data "oneDirectionFieldLocal*" one-direction-local-metrics one-direction-local-metrics
printf '[\33[01;32m  stretched  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "stretched*" stretched stretched
python plots/plotter.py plots/errors.yml data "stretched*" stretched-errors stretched-errors
python plots/plotter.py plots/cluster-node-count.yml data "stretched*" stretched-count stretched-count
python plots/plotter.py plots/metrics.yml data "stretched*" stretched-metrics stretched-metrics
printf '[\33[01;32m  uniform  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "uniformLayers*" uniform uniform
python plots/plotter.py plots/metrics.yml data "uniformLayers*" uniform-metrics uniform-metrics
printf '[\33[01;32m  movement \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "standardPatternsMovement*" movement movement
python plots/plotter.py plots/cluster-node-count.yml data "standardPatternsMovement*" movement-count movement-count
python plots/plotter.py plots/errors.yml data "standardPatternsMovement*" movement-errors movement-errors
python plots/plotter.py plots/metrics.yml data "standardPatternsMovement*" movement-metrics movement-metrics
printf '[\33[01;32m  overlay \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "overlay*" overlay overlay
python plots/plotter.py plots/cluster-node-count.yml data "overlay*" overlay-count overlay-count
python plots/plotter.py plots/errors.yml data "overlay*" overlay-errors overlay-errors
python plots/plotter.py plots/metrics.yml data "overlay*" overlay-metrics overlay-metrics

