#!/bin/bash

printf '[\33[01;32m  gaussian  \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "standardPatterns_seed*" standard
printf '[\33[01;32m  non convex  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "nonConvex*" non-convex
python plots/plotter.py plots/errors.yml data "nonConvex*" non-convex-errors
python plots/plotter.py plots/cluster-node-count.yml data "nonConvex*" non-convex-count
printf '[\33[01;32m  one direction  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionField_seed*" one-direction
printf '[\33[01;32m  one direction with local minimum  \33[01;37m]\n'
python plots/plotter.py plots/config-one.yml data "oneDirectionFieldLocal*" one-direction-local
printf '[\33[01;32m  stretched  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "stretched*" stretched
printf '[\33[01;32m  uniform  \33[01;37m]\n'
python plots/plotter.py plots/config-two.yml data "uniformLayers*" uniform
printf '[\33[01;32m  movement \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "standardPatternsMovement*" movement
python plots/plotter.py plots/cluster-node-count.yml data "standardPatternsMovement*" movement-count
python plots/plotter.py plots/errors.yml data "standardPatternsMovement*" movement-errors
python plots/plotter.py plots/metrics.yml data "standardPatternsMovement*" movement-metrics
printf '[\33[01;32m  overlay \33[01;37m]\n'
python plots/plotter.py plots/config-five.yml data "overlay*" overlay

