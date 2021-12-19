#!/bin/bash

printf '[\33[01;32m  gaussian  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "standardPatterns_seed*" standard
printf '[\33[01;32m  non convex  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "nonConvex*" non-convex
printf '[\33[01;32m  one direction  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "oneDirectionField_seed*" one-direction
printf '[\33[01;32m  one direction with local minimum  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "oneDirectionFieldLocal*" one-direction-local
printf '[\33[01;32m  stretched  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "stretched*" stretched
printf '[\33[01;32m  uniform  \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "uniformLayers*" uniform
printf '[\33[01;32m  movement \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "standardPatternsMovement*" movement
python plots/plotter.py plots/cluster-node-count.yml data "standardPatternsMovement*" movement-count
python plots/plotter.py plots/errors.yml data "standardPatternsMovement*" movement-errors
printf '[\33[01;32m  overlay \33[01;37m]\n'
python plots/plotter.py plots/config.yml data "overlay*" overlay

